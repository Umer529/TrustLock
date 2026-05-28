package com.example.trustlock.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenPactDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Disabling will allow ScreenPact to be uninstalled without guardian approval.";
    }
}
