import 'dart:math';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:ble_peripheral/ble_peripheral.dart';
import 'package:flutter/widgets.dart';

void main() {
  runApp(const App());
}

class App extends StatelessWidget {
  const App({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: "App",
      home: MainPage(),
    );
  }
}

class MainPage extends StatefulWidget {
  const MainPage({Key? key}) : super(key: key);

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  Future? future;
  BlePeripheral blePeripheral = BlePeripheral();

  @override
  initState() {
    super.initState();
    blePeripheral.listen();
  }

  handleTest(String action) {
    switch (action) {
      case "checkForHardwareSupport":
        setState(() {
          future = blePeripheral.checkForHardwareSupport();
        });
        break;
      case "initiateGattServer":
        setState(() {
          future = blePeripheral.initiateGattServer();
        });
        break;
      case "testEventChanel":
        blePeripheral.testEventChannel(Random().nextInt(100).toString());
        break;
      case "createServiceAndAdvertice":
        setState(() {
          future = createServiceAndAdvertice();
        });
        break;
    }
  }

  createServiceAndAdvertice() async {
    String serviceUuid = "0000ff10-0000-1000-8000-00805f9b34fb";
    Map<String, dynamic> service = {
      "uuid": serviceUuid,
      "characteristics": [
        {
          "uuid": "0000ff11-0000-1000-8000-00805f9b34fb",
          "properties": BleProperties.read | BleProperties.write,
          "permissions": BlePermissions.readable | BlePermissions.writable,
          "descriptors": [
            {"uuid": "00002901-0000-1000-8000-00805f9b34fb", "permissions": 0x00, "value": [0x00]}
          ]
        }
      ]
    };

    await blePeripheral.createServiceFromJson(service);
    // await blePeripheral.addServicesToGattServer([serviceUuid]);
    await blePeripheral.advertiseServices(deviceName: "test", services: [serviceUuid]);
  }

  Widget buildResult() {
    if (future != null) {
      return FutureBuilder(
          future: future,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.waiting) {
              if(snapshot.hasError) {
                return Text(snapshot.error.toString());
              }

              return Text(snapshot.data!.toString());
            }

            return const Text("Waiting...");
          });
    }
    return const SizedBox();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Container(
          constraints: const BoxConstraints.expand(),
          child: SingleChildScrollView(
            child: Column(
              children: [
                TextButton(
                    onPressed: () => handleTest("checkForHardwareSupport"),
                    child: const Text("Check For Hardware Support")),
                TextButton(onPressed: () => handleTest("initiateGattServer"), child: const Text("Initiate Gatt Server")),
                TextButton(onPressed: () => handleTest("testEventChanel"), child: const Text("testEventChanel")),
                TextButton(onPressed: () => handleTest("createServiceAndAdvertice"), child: const Text("createServiceAndAdvertice")),
                buildResult()
              ],
              mainAxisSize: MainAxisSize.max,
            ),
          ),
        ),
      ),
    );
  }
}
