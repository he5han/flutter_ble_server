import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';

class BleProperties {
  static const int read = 0x02;
  static const int write = 0x08;
  static const int notify = 0x10;
  static const int indicate = 0x20;
  static const int readNoResponse = 0x04;
}

/*
Characteristic premissions are not consistent across platforms. This will need to be reconciled.
Maybe permissions should be optional and default to read/write based on the properties.

    iOS permissions CBCharacteristic.h

    CBAttributePermissionsReadable					        = 0x01,
    CBAttributePermissionsWriteable					        = 0x02,
    CBAttributePermissionsReadEncryptionRequired	  = 0x04,
    CBAttributePermissionsWriteEncryptionRequired	  = 0x08

    Android permissions BluetoothGattCharacteristic.java

    public static final int PERMISSION_READ                   = 0x01;
    public static final int PERMISSION_READ_ENCRYPTED         = 0x02;
    public static final int PERMISSION_READ_ENCRYPTED_MITM    = 0x04;
    public static final int PERMISSION_WRITE                  = 0x10;
    public static final int PERMISSION_WRITE_ENCRYPTED        = 0x20;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM   = 0x40;
    public static final int PERMISSION_WRITE_SIGNED           = 0x80;
    public static final int PERMISSION_WRITE_SIGNED_MITM      = 0x100;
*/

class BlePermissions {
  static const int readable = 0x01;
  static final int writable = Platform.isIOS ? 0x02 : 0x10;
  static final int readEncryptionRequired = Platform.isIOS ? 0x04 : 0x02;
  static final int writeEncryptionRequired = Platform.isIOS ? 0x08 : 0x20;
}

/*
    Android permissions BluetoothGattDescriptor.class

    public static final int PERMISSION_READ                   = 1;
    public static final int PERMISSION_READ_ENCRYPTED         = 2;
    public static final int PERMISSION_READ_ENCRYPTED_MITM    = 4;
    public static final int PERMISSION_WRITE                  = 16;
    public static final int PERMISSION_WRITE_ENCRYPTED        = 32;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM   = 64;
    public static final int PERMISSION_WRITE_SIGNED           = 128;
    public static final int PERMISSION_WRITE_SIGNED_MITM      = 256;
*/

class BleDescriptorPermissions {
  static const int readable = 0x01;
  static final int writable = Platform.isIOS ? 0x02 : 0x10;
  static final int readEncryptionRequired = Platform.isIOS ? 0x04 : 0x02;
  static final int writeEncryptionRequired = Platform.isIOS ? 0x08 : 0x20;
}

class Characteristic {
  dynamic value;
  final String uuid;
  final int properties;
  final int permissions;
  final List<Discriptor> discriptors;

  Characteristic({required this.uuid, required this.properties, required this.permissions, required this.discriptors});
}

class Service {
  final List<Characteristic> characteristics;
  final String uuid;

  Service({required this.characteristics, required this.uuid});
}

class Discriptor {
  final int permission;
  final String uuid;

  Discriptor({required this.permission, required this.uuid});
}

class BlePeripheral {
  static const MethodChannel _channel = MethodChannel('ble_peripheral');
  static const EventChannel _eventChannel = EventChannel('ble_peripheral_ec');

  Future checkForHardwareSupport() {
    return _channel.invokeMethod("checkForHardwareSupport");
  }

  Future initiateGattServer() {
    return _channel.invokeMethod("init");
  }

  listen() {
    _eventChannel.receiveBroadcastStream().listen((event) { print(event);});
  }

  createServiceFromJson(Map<String, dynamic> serviceData) {
    return _channel.invokeMethod('createServiceFromJson', jsonEncode(serviceData));
  }

  addServicesToGattServer(List<String> services) {
    return _channel.invokeMethod('addServicesToGattServer', {
      "uuids": services
    });
  }

  testEventChannel(String message) {
    return _channel.invokeMethod('testEventChannel', message);
  }

  advertiseServices({required List<String> services, required String deviceName}) {
    return _channel.invokeMethod('advertiseServices', jsonEncode({
      "deviceName": deviceName,
      "uuids": services
    }));
  }

  setCharacteristicValue() async {}
}
