package com.chillotgcontro.connection

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.view.InputDevice

object UsbDevices {
    /** Thiết bị USB vật lý được liệt kê bởi hệ thống; bàn phím/chuột BT không tính là OTG USB. */
    fun list(context: Context): List<String> {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val physicalIds = usb.deviceList.values.map { it.deviceId }.toSet()
        val input = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        return InputDevice.getDeviceIds().mapNotNull { id ->
            val d = InputDevice.getDevice(id) ?: return@mapNotNull null
            if (id in physicalIds || (d.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD || (d.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                val kind = buildList { if (d.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) add("bàn phím"); if (d.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) add("chuột") }.joinToString(" + ")
                if (kind.isNotBlank()) "${d.name} · $kind" else null
            } else null
        }.distinct()
    }
}
