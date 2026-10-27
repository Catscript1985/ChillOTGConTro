package com.chillotgcontro.connection

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

object UsbDevices {
    /** Liệt kê HID đang cắm qua USB host/OTG; bàn phím Bluetooth không bị báo nhầm thành OTG. */
    fun list(context: Context): List<String> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values
            .filter(::isHid)
            .map { device ->
                val name = listOfNotNull(device.manufacturerName, device.productName)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                    .ifBlank { "USB HID · ${device.deviceName.substringAfterLast('/')}" }
                val kinds = buildList {
                    if (hasInterface(device, UsbConstants.USB_CLASS_HID, 1)) add("bàn phím")
                    if (hasInterface(device, UsbConstants.USB_CLASS_HID, 2)) add("chuột")
                }
                if (kinds.isEmpty()) "$name · USB HID" else "$name · ${kinds.joinToString(" + ")}"
            }
            .distinct()
    }

    private fun isHid(device: UsbDevice): Boolean =
        device.deviceClass == UsbConstants.USB_CLASS_HID ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_HID }

    private fun hasInterface(device: UsbDevice, clazz: Int, subclass: Int): Boolean =
        (0 until device.interfaceCount).any {
            val intf = device.getInterface(it)
            intf.interfaceClass == clazz && intf.interfaceSubclass == subclass
        }
}
