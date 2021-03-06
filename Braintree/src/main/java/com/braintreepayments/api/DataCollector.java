package com.braintreepayments.api;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.ConfigurationListener;
import com.braintreepayments.api.internal.UUIDHelper;
import com.braintreepayments.api.models.Configuration;
import com.devicecollector.DeviceCollector;
import com.devicecollector.DeviceCollector.ErrorCode;
import com.devicecollector.DeviceCollector.StatusListener;
import com.paypal.android.sdk.data.collector.SdkRiskComponent;
import com.paypal.android.sdk.onetouch.core.PayPalOneTouchCore;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * DataCollector is used to collect device information to aid in fraud detection and prevention.
 */
public class DataCollector {

    private static final String DEVICE_SESSION_ID_KEY = "device_session_id";
    private static final String FRAUD_MERCHANT_ID_KEY = "fraud_merchant_id";
    private static final String CORRELATION_ID_KEY = "correlation_id";

    private static final String BRAINTREE_MERCHANT_ID = "600000";
    private static final String SANDBOX_DEVICE_COLLECTOR_URL = "https://assets.braintreegateway.com/sandbox/data/logo.htm";
    private static final String PRODUCTION_DEVICE_COLLECTOR_URL = "https://assets.braintreegateway.com/data/logo.htm";

    private static Object sDeviceCollector;

    /**
     * @deprecated Use {@link #collectDeviceData(BraintreeFragment, BraintreeResponseListener)} instead.
     */
    @Deprecated
    public static String collectDeviceData(BraintreeFragment fragment) {
        return collectDeviceData(fragment, BRAINTREE_MERCHANT_ID);
    }

    /**
     * @deprecated Use {@link #collectDeviceData(BraintreeFragment, String, BraintreeResponseListener)} instead.
     */
    @Deprecated
    public static String collectDeviceData(BraintreeFragment fragment, String merchantId) {
        JSONObject deviceData = new JSONObject();

        try {
            String deviceSessionId = UUIDHelper.getFormattedUUID();
            startDeviceCollector(fragment, merchantId, deviceSessionId);
            deviceData.put(DEVICE_SESSION_ID_KEY, deviceSessionId);
            deviceData.put(FRAUD_MERCHANT_ID_KEY, merchantId);
        } catch (ClassNotFoundException | NoClassDefFoundError | JSONException ignored) {}

        try {
            String clientMetadataId = getPayPalClientMetadataId(fragment.getApplicationContext());
            if (!TextUtils.isEmpty(clientMetadataId)) {
                deviceData.put(CORRELATION_ID_KEY, clientMetadataId);
            }
        } catch (JSONException ignored) {}

        return deviceData.toString();
    }

    /**
     * Collect device information for fraud identification purposes.
     *
     * @param fragment {@link BraintreeFragment}
     * @param listener to be called with the device data String to send to Braintree.
     */
    public static void collectDeviceData(BraintreeFragment fragment, BraintreeResponseListener<String> listener) {
        collectDeviceData(fragment, null, listener);
    }

    /**
     * Collect device information for fraud identification purposes. This should be used in conjunction
     * with a non-aggregate fraud id.
     *
     * @param fragment {@link BraintreeFragment}
     * @param merchantId The fraud merchant id from Braintree.
     * @param listener listener to be called with the device data String to send to Braintree.
     */
    public static void collectDeviceData(final BraintreeFragment fragment, final String merchantId,
            final BraintreeResponseListener<String> listener) {
        fragment.waitForConfiguration(new ConfigurationListener() {
            @Override
            public void onConfigurationFetched(Configuration configuration) {
                JSONObject deviceData = new JSONObject();
                if (configuration.getKount().isEnabled()) {
                    String id = configuration.getKount().getKountMerchantId();
                    if (merchantId != null) {
                        id = merchantId;
                    }

                    try {
                        String deviceSessionId = UUIDHelper.getFormattedUUID();
                        startDeviceCollector(fragment, id, deviceSessionId);
                        deviceData.put(DEVICE_SESSION_ID_KEY, deviceSessionId);
                        deviceData.put(FRAUD_MERCHANT_ID_KEY, id);
                    } catch (ClassNotFoundException | NoClassDefFoundError | JSONException ignored) {}
                }

                try {
                    String clientMetadataId = getPayPalClientMetadataId(fragment.getApplicationContext());
                    if (!TextUtils.isEmpty(clientMetadataId)) {
                        deviceData.put(CORRELATION_ID_KEY, clientMetadataId);
                    }
                } catch (JSONException ignored) {}

                listener.onResponse(deviceData.toString());
            }
        });
    }

    /**
     * @deprecated Use {@link #collectDeviceData(BraintreeFragment)} instead.
     */
    @Deprecated
    public static String collectDeviceData(Context context, BraintreeFragment fragment) {
        return collectDeviceData(fragment);
    }

    /**
     * @deprecated Use {@link #collectDeviceData(BraintreeFragment, String)} instead.
     */
    @Deprecated
    public static String collectDeviceData(Context context, BraintreeFragment fragment, String merchantId) {
        return collectDeviceData(fragment, merchantId);
    }

    /**
     * Collect device information for fraud identification purposes from PayPal only.
     *
     * @param context A valid {@link Context}
     * @return The client metadata id associated with the collected data.
     */
    public static String getPayPalClientMetadataId(Context context) {
        try {
            return PayPalOneTouchCore.getClientMetadataId(context);
        } catch (NoClassDefFoundError ignored) {}

        try {
            return SdkRiskComponent.getClientMetadataId(context, UUIDHelper.getPersistentUUID(context), null);
        } catch (NoClassDefFoundError ignored) {}

        return "";
    }

    private static void startDeviceCollector(final BraintreeFragment fragment,
            final String merchantId, final String deviceSessionId) throws ClassNotFoundException {
        Class.forName(DataCollector.class.getName());
        fragment.waitForConfiguration(new ConfigurationListener() {
            @Override
            public void onConfigurationFetched(Configuration configuration) {
                DeviceCollector deviceCollector = new DeviceCollector(fragment.getActivity());
                sDeviceCollector = deviceCollector;
                deviceCollector.setMerchantId(merchantId);
                deviceCollector.setCollectorUrl(getDeviceCollectorUrl(configuration.getEnvironment()));
                deviceCollector.setStatusListener(new StatusListener() {
                    @Override
                    public void onCollectorStart() {}

                    @Override
                    public void onCollectorSuccess() {
                        sDeviceCollector = null;
                    }

                    @Override
                    public void onCollectorError(ErrorCode errorCode, Exception e) {
                        sDeviceCollector = null;
                    }
                });

                deviceCollector.collect(deviceSessionId);
            }
        });
    }

    @VisibleForTesting
    static String getDeviceCollectorUrl(String environment) {
        if ("production".equalsIgnoreCase(environment)) {
            return PRODUCTION_DEVICE_COLLECTOR_URL;
        }
        return SANDBOX_DEVICE_COLLECTOR_URL;
    }
}
