package com.example.wdoverlay

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import sun.security.x509.*
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.Executors

class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val keyFile = File(context.filesDir, "adb_key.pk8")
    private val certFile = File(context.filesDir, "adb_cert.der")
    private val privKey: PrivateKey
    private val cert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        if (keyFile.exists() && certFile.exists()) {
            privKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = certFile.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val subject = X500Name("CN=WDOverlay")
            val from = Date()
            val to = Date(from.time + 10L * 365 * 24 * 3600 * 1000)
            val info = X509CertInfo().apply {
                set(X509CertInfo.VALIDITY, CertificateValidity(from, to))
                set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(BigInteger(64, SecureRandom())))
                set(X509CertInfo.SUBJECT, subject)
                set(X509CertInfo.ISSUER, subject)
                set(X509CertInfo.KEY, CertificateX509Key(kp.public))
                set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
                set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")))
            }
            val x509 = X509CertImpl(info).also { it.sign(kp.private, "SHA256withRSA") }
            privKey = kp.private
            cert = x509
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(x509.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privKey
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "WDOverlay"

    companion object {
        @Volatile private var instance: AdbManager? = null
        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }
}

object Adb {
    private val pool = Executors.newSingleThreadExecutor()

    fun run(block: () -> String, onResult: (String) -> Unit) {
        pool.execute {
            val text = try { block() } catch (t: Throwable) { "Error: ${t.message ?: t.javaClass.simpleName}" }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(text) }
        }
    }

    fun pair(ctx: Context, port: Int, code: String): String {
        val ok = AdbManager.get(ctx).pair("127.0.0.1", port, code)
        return if (ok) "Paired. Now enter the debug port and press Connect." else "Pairing failed."
    }

    fun connect(ctx: Context, port: Int): String {
        val ok = AdbManager.get(ctx).connect("127.0.0.1", port)
        return if (ok) "Connected." else "Connect failed."
    }

    fun isConnected(ctx: Context): Boolean = AdbManager.get(ctx).isConnected

    fun shell(ctx: Context, command: String): String {
        val m = AdbManager.get(ctx)
        if (!m.isConnected) return "Not connected."
        val stream = m.openStream("shell:$command")
        return stream.openInputStream().bufferedReader().use { it.readText() }.trim().ifEmpty { "(no output)" }
    }
}
