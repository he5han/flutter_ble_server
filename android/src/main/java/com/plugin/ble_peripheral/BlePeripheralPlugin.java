package com.plugin.ble_peripheral;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * BlePeripheralPlugin
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BlePeripheralPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware {
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothGattServer bluetoothGattServer;

    private MethodChannel channel;
    private Activity activity;
    private EventChannel eventChannel;

    private final Map<String, BluetoothGattService> serviceRepo = new HashMap();

    private @Nullable
    EventChannel.EventSink eventSink;

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            eventSink.success("{\"action\": \"onDescriptorWriteRequest\"}");
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
                if (device.getBondState() != BluetoothDevice.BOND_BONDING &&
                        device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    device.createBond();
                } else if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                    device.setPairingConfirmation(true);
                }

        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            eventSink.success("{\"action\": \"advertiseCallback\", \"payload\": {\"status\":\"Success\"}}");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            String message;
            switch (errorCode) {
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    message = "ADVERTISE_FAILED_DATA_TOO_LARGE";
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    message = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    message = "ADVERTISE_FAILED_INTERNAL_ERROR";
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    message = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
                    break;
                default:
                    message = "ADVERTISE_FAILED_ALREADY_STARTED";
            }

            notifyEventChannel(new BlePluginEventChanelNotification("advertiseCallback")
                    .addData("status", "Error")
                    .addData("message", message));
        }
    };

    private void notifyEventChannel(BlePluginEventChanelNotification notification) {
        if(eventSink != null) {
            eventSink.success(notification.toString());
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "ble_peripheral");
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "ble_peripheral_ec");
        eventChannel.setStreamHandler(this);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "testEventChannel" : {
                String message = (String) call.arguments;
                notifyEventChannel(new BlePluginEventChanelNotification("testEventChannel")
                        .addData("status", "OK")
                        .addData("message", message));
                break;
            }
            case "checkForHardwareSupport": {
                boolean hardwareSupportsBLE = activity.getApplicationContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
                if (hardwareSupportsBLE)
                    result.success("OK");
                else
                    result.error("checkForHardwareSupport", "This hardware does not support Bluetooth Low Energy", null);
                break;
            }
            case "init": {
                try {
                    bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
                    bluetoothAdapter = bluetoothManager.getAdapter();
                    bluetoothGattServer = bluetoothManager.openGattServer(activity.getBaseContext(), bluetoothGattServerCallback);
                    result.success("OK");
                } catch (Exception err) {
                    err.printStackTrace();
                    result.error("init", err.getMessage(), err.getCause());
                }
                break;
            }
            case "createServiceFromJson": {
                try {
                    JSONObject object = new JSONObject((String) call.arguments);
                    bluetoothGattServer.addService(ServiceBuilder.fromJson(object));
//                    serviceRepo.put(object.getString("uuid"), ServiceBuilder.fromJson(object));
                    result.success("OK");
                } catch (Exception err) {
                    err.printStackTrace();
                    result.error("createServiceFromJson", err.getMessage(), err.getCause());
                }
                break;
            }
            case "addServicesToGattServer": {
                try{
                    bluetoothGattServer.addService(serviceRepo.get((List<String>) call.argument("uuids")));
                    result.success("OK");
                    break;
                } catch (Exception err) {
                    err.printStackTrace();
                    result.error("addServicesToGattServer", err.getMessage(), err.getCause());
                }
            }
            case "startGattServer": {
                break;
            }
            case "advertiseServices": {
                try {
                    JSONObject object = new JSONObject((String) call.arguments);
                    JSONArray uuids = object.getJSONArray("uuids");
                    String name = object.getString("deviceName");

                    bluetoothAdapter.setName(name);

                    AdvertiseData.Builder builder = new AdvertiseData.Builder();

                    for (int i = 0; i < uuids.length(); i++) {
                        builder.addServiceUuid(ParcelUuid.fromString(uuids.getString(i)));
                    }

                    AdvertiseData advertisementData = builder
                            .setIncludeDeviceName(true)
                            .setIncludeTxPowerLevel(false)
                            .build();

                    AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                            .setConnectable(true)
                            .build();

                    BluetoothLeAdvertiser bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                    bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertisementData, advertiseCallback);

                    result.success("OK");
                } catch (Exception err) {
                    result.error("advertiseService", err.getMessage(), err.getCause());
                }
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        eventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {
        eventSink = null;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }
}

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class ServiceBuilder {
    private UUID uuid;
    private final List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();

    public static BluetoothGattService fromJson(JSONObject jsonObject) throws JSONException {
        ServiceBuilder serviceBuilder = new ServiceBuilder()
                .setUuid(UUID.fromString(jsonObject.getString("uuid")));

        JSONArray characteristics = jsonObject.getJSONArray("characteristics");
        for (int ci = 0; ci < characteristics.length(); ci++) {
            JSONObject characteristic = characteristics.getJSONObject(ci);
            CharacteristicBuilder characteristicBuilder = new CharacteristicBuilder()
                    .setUuid(UUID.fromString(characteristic.getString("uuid")))
                    .setPermissions(characteristic.getInt("permissions"))
                    .setProperties(characteristic.getInt("properties"));

            JSONArray descriptors = characteristic.getJSONArray("descriptors");
            for (int di = 0; di < descriptors.length(); di++) {
                JSONObject jsonDescriptor = descriptors.getJSONObject(di);
                BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                        UUID.fromString(jsonDescriptor.getString("uuid")),
                        jsonDescriptor.getInt("permissions"));
                characteristicBuilder.addDescriptor(descriptor);

                JSONArray jsonBytes = jsonDescriptor.getJSONArray("value");

                byte[] b = new byte[jsonBytes.length()];
                for (int bi = 0; bi < jsonBytes.length(); bi++) {
                    b[bi] = Integer.valueOf(jsonBytes.getInt(bi)).byteValue();
                }

                descriptor.setValue(b);
            }

            serviceBuilder.addCharacteristic(characteristicBuilder.build());
        }

        return serviceBuilder.build();
    }

    ServiceBuilder setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    ServiceBuilder addCharacteristic(BluetoothGattCharacteristic characteristic) {
        characteristics.add(characteristic);
        return this;
    }

    BluetoothGattService build() {
        return new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    }
}

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class CharacteristicBuilder {
    private UUID uuid;
    private int properties;
    private int permissions;
    private final List<BluetoothGattDescriptor> descriptors = new ArrayList<>();

    public static boolean isNotify(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0);
    }

    private static boolean isIndicate(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0);
    }

    public CharacteristicBuilder setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public CharacteristicBuilder setProperties(int properties) {
        this.properties = properties;
        return this;
    }

    public CharacteristicBuilder setPermissions(int permissions) {
        this.permissions = permissions;
        return this;
    }

    public CharacteristicBuilder addDescriptor(BluetoothGattDescriptor descriptor) {
        descriptors.add(descriptor);
        return this;
    }

    BluetoothGattCharacteristic build() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(uuid, properties, permissions);

        if (isNotify(characteristic) || isIndicate(characteristic)) {
            addDescriptor(new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                    BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        }

        for (BluetoothGattDescriptor descriptor : descriptors) {
            characteristic.addDescriptor(descriptor);
        }

        return characteristic;
    }
}

class BlePluginEventChanelNotification {
    String action;
    private final HashMap<String, Object> payload = new HashMap();

    public BlePluginEventChanelNotification(String action) {
        this.action = action;
    }

    BlePluginEventChanelNotification addData(String key, Object value) {
        payload.put(key, value);
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        try {
            JSONObject payloadObject = new JSONObject();
            for (String key: payload.keySet()){
                payloadObject.put(key, payload.get(key));
            }

            return new JSONObject()
                    .put("action", action)
                    .put("payload", payloadObject).toString();

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }
}