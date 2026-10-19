package com.biotrack.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.units.BloodGlucose
import java.time.Instant
import java.time.ZoneId

class HealthExporter(private val context: Context, private val store: ReadingStore) {
    companion object { val permissions = setOf(HealthPermission.getWritePermission(BloodGlucoseRecord::class)) }
    fun available() = HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_AVAILABLE
    suspend fun allowed(): Boolean = available() && HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    suspend fun export(): Int {
        check(allowed()) { "Allow blood glucose sharing in Health Connect first" }
        val client=HealthConnectClient.getOrCreate(context)
        var count=0
        for(r in store.readings().filter { it.eligible && it.timeConfirmed && !it.exported }) {
            val time=Instant.ofEpochMilli(r.time)
            val metadata=if(r.source=="Manual") Metadata.manualEntry(clientRecordId="biotrack:${r.id}", clientRecordVersion=1)
                else Metadata.autoRecorded(device=Device(type=Device.TYPE_UNKNOWN),clientRecordId="biotrack:${r.id}", clientRecordVersion=1)
            client.insertRecords(listOf(BloodGlucoseRecord(time=time,
                zoneOffset=ZoneId.systemDefault().rules.getOffset(time),
                level=BloodGlucose.millimolesPerLiter(r.mmol), metadata=metadata)))
            // A crash between insert and local receipt retries the same stable client ID.
            store.exported(r.id); count++
        }
        return count
    }
}
