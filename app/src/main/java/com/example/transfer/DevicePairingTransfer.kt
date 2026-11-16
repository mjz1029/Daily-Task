package com.example.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import com.example.data.LocalTaskRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class TransferBundle(
  val version: Int = 1,
  val exportedAtMillis: Long = System.currentTimeMillis(),
  val records: List<LocalTaskRecord> = emptyList(),
)

data class PairingSessionInfo(
  val pairingCode: String,
  val port: Int,
  val localIpAddress: String?,
  val directAddress: String?,
)

object DevicePairingTransfer {
  private const val TAG = "DeviceTransfer"
  private const val NSD_SERVICE_TYPE = "_todaytasks._tcp."
  private const val OFFLINE_PREFIX = "TD1:"
  private const val MAX_FAILED_ATTEMPTS = 12
  private const val PBKDF2_ITERATIONS = 10_000

  private val json = Json { ignoreUnknownKeys = true }
  private val secureRandom = SecureRandom()

  private val httpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .build()
  }

  fun generateSixDigitCode(): String {
    val num = secureRandom.nextInt(1_000_000)
    return String.format("%06d", num)
  }

  fun formatCodeSpaced(code: String): String {
    val digits = code.filter { it.isDigit() }.take(6)
    return if (digits.length == 6) "${digits.substring(0, 3)} ${digits.substring(3, 6)}" else digits
  }

  fun findLocalIpv4Address(): String? {
    return runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      val candidates = mutableListOf<String>()
      for (iface in interfaces) {
        if (!iface.isUp || iface.isLoopback) continue
        for (addr in iface.inetAddresses) {
          if (addr is Inet4Address && !addr.isLoopbackAddress) {
            val host = addr.hostAddress ?: continue
            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
              candidates.add(0, host)
            } else {
              candidates.add(host)
            }
          }
        }
      }
      candidates.firstOrNull()
    }.getOrNull()
  }

  class PairingServer(
    private val pairingCode: String,
    private val recordsProvider: suspend () -> List<LocalTaskRecord>,
    private val onTransferredCount: (Int) -> Unit = {},
  ) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val failedAttempts = AtomicInteger(0)

    val actualPort: Int
      get() = serverSocket?.localPort ?: -1

    fun start(
      context: Context?,
      scope: CoroutineScope,
    ): PairingSessionInfo {
      stop(context)
      val socket = ServerSocket(0)
      serverSocket = socket
      val port = socket.localPort
      val ip = findLocalIpv4Address()

      if (context != null) {
        registerNsdService(context, port)
      }

      acceptJob = scope.launch(Dispatchers.IO) {
        while (isActive && !socket.isClosed) {
          val client = try {
            socket.accept()
          } catch (e: Exception) {
            break
          }
          launch(Dispatchers.IO) {
            handleClient(client)
          }
        }
      }

      return PairingSessionInfo(
        pairingCode = pairingCode,
        port = port,
        localIpAddress = ip,
        directAddress = ip?.let { "$it:$port" },
      )
    }

    private suspend fun handleClient(client: Socket) {
      client.use { sock ->
        sock.soTimeout = 5000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        while (true) {
          val header = reader.readLine() ?: break
          if (header.isEmpty()) break
        }

        val pathPart = requestLine.split(" ").getOrNull(1).orEmpty()
        val requestedCode = extractQueryParam(pathPart, "code")?.filter { it.isDigit() }.orEmpty()

        val out = sock.getOutputStream()
        if (failedAttempts.get() >= MAX_FAILED_ATTEMPTS) {
          writeHttpResponse(out, 429, "Too Many Requests", """{"error":"错误尝试次数过多，请重新生成配对码"}""")
          return
        }

        val matches = requestedCode.length == 6 &&
          MessageDigest.isEqual(
            requestedCode.toByteArray(Charsets.UTF_8),
            pairingCode.toByteArray(Charsets.UTF_8),
          )

        if (!matches) {
          failedAttempts.incrementAndGet()
          writeHttpResponse(out, 403, "Forbidden", """{"error":"配对码不正确，请核对旧手机上的 6 位数字"}""")
          return
        }

        val records = recordsProvider()
        val bundle = TransferBundle(
          version = 1,
          exportedAtMillis = System.currentTimeMillis(),
          records = records,
        )
        val payloadJson = json.encodeToString(bundle)
        writeHttpResponse(out, 200, "OK", payloadJson)
        onTransferredCount(records.size)
      }
    }

    private fun registerNsdService(context: Context, port: Int) {
      runCatching {
        val mgr = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = mgr
        val info = NsdServiceInfo().apply {
          serviceName = "TodayTasks_${pairingCode.take(2)}${System.currentTimeMillis() % 1000}"
          serviceType = NSD_SERVICE_TYPE
          setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
          override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "NSD registered: ${serviceInfo.serviceName}")
          }
          override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "NSD registration failed: $errorCode")
          }
          override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
          override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
      }.onFailure {
        Log.w(TAG, "Failed to register NSD service: ${it.message}")
      }
    }

    fun stop(context: Context? = null) {
      acceptJob?.cancel()
      acceptJob = null
      runCatching { serverSocket?.close() }
      serverSocket = null
      val mgr = nsdManager ?: (context?.applicationContext?.getSystemService(Context.NSD_SERVICE) as? NsdManager)
      val listener = registrationListener
      if (mgr != null && listener != null) {
        runCatching { mgr.unregisterService(listener) }
      }
      registrationListener = null
      nsdManager = null
    }

    private fun writeHttpResponse(
      out: java.io.OutputStream,
      statusCode: Int,
      statusText: String,
      jsonBody: String,
    ) {
      val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
      val headers = buildString {
        append("HTTP/1.1 $statusCode $statusText\r\n")
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ${bodyBytes.size}\r\n")
        append("Connection: close\r\n\r\n")
      }.toByteArray(Charsets.UTF_8)
      out.write(headers)
      out.write(bodyBytes)
      out.flush()
    }

    private fun extractQueryParam(path: String, key: String): String? {
      val query = path.substringAfter('?', "")
      if (query.isBlank()) return null
      return query.split('&')
        .mapNotNull { part ->
          val kv = part.split('=', limit = 2)
          if (kv.size == 2 && kv[0] == key) kv[1] else null
        }
        .firstOrNull()
    }
  }

  suspend fun receiveOverLan(
    context: Context,
    pairingCode: String,
    manualAddress: String? = null,
  ): Result<TransferBundle> = withContext(Dispatchers.IO) {
    val cleanCode = pairingCode.filter { it.isDigit() }
    if (cleanCode.length != 6) {
      return@withContext Result.failure(IllegalArgumentException("请输入完整的 6 位数字配对码"))
    }

    val cleanManual = manualAddress?.trim().orEmpty()
    if (cleanManual.isNotBlank()) {
      return@withContext fetchFromAddress(cleanManual, cleanCode)
    }

    // Automatic LAN NSD discovery when user only enters the 6-digit pairing code
    val peers = discoverLanPeers(context, timeoutMs = 4_000L)
    if (peers.isEmpty()) {
      return@withContext Result.failure(
        IllegalStateException("同一 Wi-Fi 下未发现发送端设备。请确认两台手机在同一 Wi-Fi 并已打开「生成配对码」，或手动填写局域网地址（如 192.168.x.x:端口），也可使用「离线迁移口令」。")
      )
    }

    var lastError: Throwable? = null
    for (address in peers) {
      val attempt = fetchFromAddress(address, cleanCode)
      if (attempt.isSuccess) return@withContext attempt
      lastError = attempt.exceptionOrNull()
    }
    Result.failure(lastError ?: IllegalStateException("配对码不匹配或连接失败"))
  }

  suspend fun fetchFromAddress(hostAndPort: String, pairingCode: String): Result<TransferBundle> =
    withContext(Dispatchers.IO) {
      val normalized = hostAndPort.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .trimEnd('/')
      if (!normalized.contains(':')) {
        return@withContext Result.failure(IllegalArgumentException("地址格式应为 IP:端口，例如 192.168.1.8:49321"))
      }
      val cleanCode = pairingCode.filter { it.isDigit() }
      val url = "http://$normalized/transfer?code=$cleanCode"
      try {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val errMsg = runCatching {
              org.json.JSONObject(body).optString("error")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "配对码校验失败（HTTP ${response.code}）"
            return@withContext Result.failure(IllegalStateException(errMsg))
          }
          val bundle = json.decodeFromString<TransferBundle>(body)
          Result.success(bundle)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(IllegalStateException("无法连接发送端 (${e.localizedMessage ?: "请检查网络"})"))
      }
    }

  private suspend fun discoverLanPeers(context: Context, timeoutMs: Long): List<String> {
    val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
      ?: return emptyList()
    val discoveredAddresses = mutableListOf<String>()
    val firstFoundSignal = CompletableDeferred<Unit>()
    var discoveryListener: NsdManager.DiscoveryListener? = null

    try {
      discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
          if (!serviceInfo.serviceType.contains("_todaytasks")) return
          @Suppress("DEPRECATION")
          nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
              override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
              override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = info.host?.hostAddress
                val port = info.port
                if (!host.isNullOrBlank() && port > 0) {
                  synchronized(discoveredAddresses) {
                    val addr = "$host:$port"
                    if (addr !in discoveredAddresses) discoveredAddresses.add(addr)
                  }
                  firstFoundSignal.complete(Unit)
                }
              }
            },
          )
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          firstFoundSignal.complete(Unit)
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
      }

      nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
      withTimeoutOrNull(timeoutMs) {
        firstFoundSignal.await()
        delay(350L) // allow briefly for other peers if multiple exist
      }
    } catch (e: Exception) {
      Log.w(TAG, "NSD discovery failed: ${e.message}")
    } finally {
      if (discoveryListener != null) {
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
      }
    }

    return synchronized(discoveredAddresses) { discoveredAddresses.toList() }
  }

  fun encodeOfflineEncryptedPackage(
    records: List<LocalTaskRecord>,
    pairingCode: String,
  ): String {
    val cleanCode = pairingCode.filter { it.isDigit() }
    require(cleanCode.length == 6) { "配对码必须为 6 位数字" }

    val bundle = TransferBundle(
      version = 1,
      exportedAtMillis = System.currentTimeMillis(),
      records = records,
    )
    val jsonBytes = json.encodeToString(bundle).toByteArray(Charsets.UTF_8)
    val compressed = gzipCompress(jsonBytes)

    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
    val key = deriveAesKey(cleanCode, salt)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(compressed)

    val combined = ByteArray(salt.size + iv.size + ciphertext.size)
    System.arraycopy(salt, 0, combined, 0, salt.size)
    System.arraycopy(iv, 0, combined, salt.size, iv.size)
    System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)

    val b64 = Base64.encodeToString(combined, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    return "$OFFLINE_PREFIX$b64"
  }

  fun decodeOfflineEncryptedPackage(
    packageText: String,
    pairingCode: String,
  ): Result<TransferBundle> {
    val cleanCode = pairingCode.filter { it.isDigit() }
    if (cleanCode.length != 6) {
      return Result.failure(IllegalArgumentException("请输入 6 位数字配对码"))
    }
    val trimmed = packageText.trim()
    if (!trimmed.startsWith(OFFLINE_PREFIX)) {
      return Result.failure(IllegalArgumentException("迁移口令格式无效（应以 TD1: 开头）"))
    }
    return try {
      val rawBytes = Base64.decode(
        trimmed.removePrefix(OFFLINE_PREFIX),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )
      if (rawBytes.size < 29) {
        return Result.failure(IllegalArgumentException("迁移口令内容不完整"))
      }
      val salt = rawBytes.copyOfRange(0, 16)
      val iv = rawBytes.copyOfRange(16, 28)
      val ciphertext = rawBytes.copyOfRange(28, rawBytes.size)

      val key = deriveAesKey(cleanCode, salt)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      val compressed = cipher.doFinal(ciphertext)
      val jsonStr = gzipDecompress(compressed).toString(Charsets.UTF_8)
      val bundle = json.decodeFromString<TransferBundle>(jsonStr)
      Result.success(bundle)
    } catch (e: Exception) {
      Result.failure(IllegalStateException("配对码不正确或迁移口令已损坏"))
    }
  }

  private fun deriveAesKey(code: String, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(code.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val bytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(bytes, "AES")
  }

  private fun gzipCompress(input: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use { it.write(input) }
    return bos.toByteArray()
  }

  private fun gzipDecompress(input: ByteArray): ByteArray {
    val bis = ByteArrayInputStream(input)
    GZIPInputStream(bis).use { gis ->
      return gis.readBytes()
    }
  }
}
