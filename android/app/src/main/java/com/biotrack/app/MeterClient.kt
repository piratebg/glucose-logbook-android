package com.biotrack.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import java.util.UUID
import kotlin.math.max

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@SuppressLint("MissingPermission")
class MeterClient(private val app: Application, private val store: ReadingStore) {
    companion object {
        fun uuid(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
        val GLUCOSE = uuid("1808"); val MEASUREMENT = uuid("2a18"); val CONTEXT = uuid("2a34"); val RACP = uuid("2a52"); val CCCD = uuid("2902")
    }
    private val handler = Handler(Looper.getMainLooper())
    private val adapter get() = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = app.getSharedPreferences("meter", 0)
    val devices = linkedMapOf<String, String>()
    var status = "Ready to connect your meter"; private set
    var connected = false; private set
    var scanning = false; private set
    var busy = false; private set
    var onChange: (() -> Unit)? = null
    private var gatt: BluetoothGatt? = null
    private var target: String? = null
    private var stopped = false
    private var attempts = 0
    private var stage = ""
    private var inserted = 0
    private var maxSequence: Int? = null
    private var fallback = false
    private var lastFull = false
    private val queue = ArrayDeque<Pair<BluetoothGattCharacteristic, Boolean>>()
    private var pendingDescriptor: UUID? = null
    private var timer: Runnable? = null
    private var scanTimer: Runnable? = null
    private var retryTimer: Runnable? = null
    private fun state(message: String) { status = message; onChange?.invoke() }
    fun permissions(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all { app.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED }
        else app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun deadline(ms: Long, action: () -> Unit) {
        timer?.let(handler::removeCallbacks)
        timer = Runnable(action).also { handler.postDelayed(it, ms) }
    }
    private fun clearDeadline() { timer?.let(handler::removeCallbacks); timer = null }
    fun scan() {
        if (!permissions()) { state("Allow Nearby devices permission to find your meter"); return }
        if (adapter?.isEnabled != true) { state("Turn on Bluetooth, then try again"); return }
        if (busy || connected) { state("Disconnect before searching for another meter"); return }
        stopScan(); devices.clear(); scanning = true
        try {
            // An unfiltered scan finds meters that omit service UUIDs in advertisements.
            adapter?.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            state("Searching for 30 seconds. Keep your meter awake and nearby.")
            scanTimer = Runnable { stopScan(); state(if(devices.isEmpty()) "No devices found. Put the meter in pairing mode and search again." else "Select your meter below") }.also { handler.postDelayed(it, 30000) }
        } catch (_: Exception) { scanning = false; state("Could not start Bluetooth scan. Check Bluetooth and permissions.") }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post {
                if (!scanning || !permissions()) return@post
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                if (!name.isNullOrBlank() && devices[result.device.address] != name) {
                    devices[result.device.address] = name
                    onChange?.invoke()
                }
            }
        }
        override fun onScanFailed(errorCode: Int) { handler.post { stopScan(); state("Search failed ($errorCode). Wait a moment, then retry.") } }
    }
    private fun stopScan() {
        scanTimer?.let(handler::removeCallbacks); scanTimer = null
        if(scanning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
    }
    fun connect(address: String) {
        if(!permissions()) { state("Nearby devices permission required"); return }
        stopScan(); disconnect(false); stopped = false; target = address; attempts = 0
        prefs.edit().putString("address", address).apply()
        openConnection()
    }
    fun reconnect() { prefs.getString("address", null)?.let(::connect) ?: scan() }
    private fun openConnection() {
        if(stopped || !permissions()) return
        try {
            if(adapter?.isEnabled != true) { fail("Bluetooth is off"); return }
            busy = true; stage = "connect"; state("Connecting to meter…")
            val device = adapter!!.getRemoteDevice(target!!)
            if(device.bondState == BluetoothDevice.BOND_NONE) {
                stage = "bond"; state("Confirm the meter pairing request on your phone")
                if(!device.createBond()) { fail("Pairing could not start"); return }
                deadline(60000) { fail("Pairing timed out. Keep the meter in pairing mode.") }
            } else if(device.bondState == BluetoothDevice.BOND_BONDING) {
                stage = "bond"; deadline(60000) { fail("Pairing timed out") }
            } else startGatt(device)
        } catch (_: Exception) { fail("Connection could not start. Check permissions and Bluetooth.") }
    }
    private fun startGatt(device: BluetoothDevice) {
        clearDeadline(); stage = "connect"
        gatt = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
        deadline(30000) { fail("Connection timed out. Wake the meter and keep it nearby.") }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if(intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED && permissions()) {
                @Suppress("DEPRECATION") val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if(device == null || device.address != target || stage != "bond" || stopped) return
                when(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED -> startGatt(device)
                    BluetoothDevice.BOND_NONE -> { disconnect(false); stopped = true; state("Pairing was declined or failed. Retry when ready.") }
                }
            }
        }
    }
    init {
        if(Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_EXPORTED)
        else app.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }
    private fun fail(message: String) {
        closeGatt(); busy = false
        if(!stopped && target != null && permissions() && attempts < 3) {
            attempts++; state("$message. Retrying $attempts/3…")
            retryTimer?.let(handler::removeCallbacks)
            retryTimer = Runnable { openConnection() }.also { handler.postDelayed(it, attempts * 3000L) }
        } else { stopped = true; state("$message. Tap Reconnect to try again.") }
    }
    private fun closeGatt() {
        clearDeadline(); queue.clear(); pendingDescriptor = null; connected = false; stage = ""
        val old = gatt; gatt = null
        runCatching { old?.disconnect() }; runCatching { old?.close() }
    }
    fun disconnect(show: Boolean = true) {
        stopped = true; retryTimer?.let(handler::removeCallbacks); retryTimer = null
        stopScan(); closeGatt(); busy = false
        if(show) state("Disconnected. Your saved readings are safe.")
    }
    private fun subscribeNext() {
        val connection = gatt ?: return
        if(queue.isEmpty()) {
            clearDeadline(); connected = true; busy = false; stage = "ready"; attempts = 0
            state("Connected. Checking for new readings…"); history(false); return
        }
        val (characteristic, indication) = queue.removeFirst()
        try {
            check(connection.setCharacteristicNotification(characteristic, true))
            val descriptor = characteristic.getDescriptor(CCCD) ?: error("Missing subscription descriptor")
            pendingDescriptor = characteristic.uuid
            val value = if(indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val ok = if(Build.VERSION.SDK_INT >= 33) connection.writeDescriptor(descriptor, value)==BluetoothStatusCodes.SUCCESS else {
                descriptor.value = value
                connection.writeDescriptor(descriptor)
            }
            check(ok); deadline(20000) { fail("Meter subscription timed out") }
        } catch (_: Exception) { fail("Could not subscribe to meter data") }
    }
    fun history(full: Boolean) { fallback = false; requestHistory(full) }
    private fun requestHistory(full: Boolean) {
        val connection = gatt ?: return
        if(!connected || busy) return
        try {
            val characteristic = connection.getService(GLUCOSE)?.getCharacteristic(RACP) ?: error("Missing history control")
            val checkpoint = if(full) null else store.checkpoint(target!!)?.let { max(0, it-20) }
            val bytes = GlucoseProtocol.requestSince(checkpoint)
            lastFull = checkpoint == null || bytes.size==2
            maxSequence = null; inserted = 0; busy = true; stage = "history"
            state(if(lastFull) "Importing meter history…" else "Checking for new readings…")
            val ok = if(Build.VERSION.SDK_INT >= 33) connection.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                connection.writeCharacteristic(characteristic)
            }
            check(ok); deadline(90000) { fail("History transfer timed out. Saved readings will be kept.") }
        } catch (_: Exception) { fail("Could not request meter history") }
    }
    private fun packet(connection: BluetoothGatt, uuid: UUID, bytes: ByteArray) {
        if(connection !== gatt) return
        if(uuid == MEASUREMENT) {
            try {
                val reading = GlucoseProtocol.measurement(bytes)
                if(store.save(target!!, reading)) inserted++
                if(stage=="history") maxSequence = max(maxSequence ?: 0, reading.sequence)
                if(stage=="history") deadline(90000) { fail("History transfer stalled. Saved readings will be kept.") }
                state("Saved $inserted new reading${if(inserted==1) "" else "s"}${if(busy) "; transfer in progress…" else ". Meter connected."}")
            } catch (_: Exception) { fail("An unreadable meter reading was received. Retry full history.") }
        } else if(uuid == CONTEXT) {
            try { store.context(target!!,bytes) } catch (_: Exception) { fail("Unreadable measurement context; retry full history") }
        } else if(uuid == RACP && stage == "history") {
            if(bytes.size != 4 || bytes[0].toInt()!=6 || bytes[1].toInt()!=0 || bytes[2].toInt()!=1) { fail("Unexpected history response"); return }
            val result = bytes[3].toInt() and 255
            if(result==1 || result==6) {
                clearDeadline(); busy = false; stage = "ready"
                if(result==6 && !lastFull && !fallback) { fallback=true; requestHistory(true); return }
                maxSequence?.let { store.complete(target!!, it) }
                state("Up to date · $inserted new readings saved. Listening while connected.")
            } else fail("Meter rejected history request ($result)")
        }
        // Context is retained separately; it does not alter base measurement identity.
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) { handler.post {
            if(connection !== gatt) return@post
            if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED) { fail("Meter disconnected ($status)"); return@post }
            if(newState==BluetoothProfile.STATE_CONNECTED) {
                stage = "discovery"; state("Finding glucose services…")
                try { if(!connection.discoverServices()) fail("Service discovery could not start") else deadline(20000) { fail("Service discovery timed out") } }
                catch (_: Exception) { fail("Bluetooth permission required") }
            }
        } }
        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) { handler.post {
            if(connection !== gatt) return@post
            if(status!=0) { fail("Service discovery failed ($status)"); return@post }
            val service = connection.getService(GLUCOSE)
            val measurement = service?.getCharacteristic(MEASUREMENT)
            val racp = service?.getCharacteristic(RACP)
            if(measurement==null || racp==null || measurement.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY==0 || racp.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE==0) {
                stopped=true; fail("This device does not expose the supported Bluetooth glucose profile"); return@post
            }
            stage="subscribe"; queue.clear(); queue.add(measurement to false)
            service.getCharacteristic(CONTEXT)?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0 }?.let { queue.add(it to false) }
            queue.add(racp to true); subscribeNext()
        } }
        override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { handler.post {
            if(connection !== gatt || stage!="subscribe" || descriptor.uuid!=CCCD || descriptor.characteristic.uuid!=pendingDescriptor) return@post
            if(status!=0) fail("Subscription failed ($status)") else subscribeNext()
        } }
        override fun onCharacteristicWrite(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { handler.post {
            if(connection === gatt && characteristic.uuid==RACP && status!=0) fail("History request failed ($status)")
        } }
        @Deprecated("Legacy Android callback")
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if(Build.VERSION.SDK_INT<33) { @Suppress("DEPRECATION") val bytes=characteristic.value?.copyOf() ?: return; handler.post { packet(connection, characteristic.uuid, bytes) } }
        }
        override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val bytes=value.copyOf(); handler.post { packet(connection, characteristic.uuid, bytes) }
        }
    }
}
