package com.android.settings.network;

import android.Manifest;
import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.ext.PackageId;
import android.os.PowerManager;
import android.os.UserHandle;
import android.permission.PermissionManager;

import com.android.settings.R;
import com.android.settings.ext.AbstractTogglePrefController;
import com.android.settings.ext.ExtSettingControllerHelper;

import static java.util.Objects.requireNonNull;

public class GoogleEuiccLpaController extends AbstractTogglePrefController {
    // DiamaneOS: the switch also turns DiamaneOS's own eSIM manager on and off. It controls the
    // first of these packages that is installed as a system app.
    private static final String[] LPA_PACKAGES = {
            "de.diamaneos.euicc",
            PackageId.G_EUICC_LPA_NAME,
    };

    private final PackageManager packageManager;
    private final String pkgName;
    private final boolean isPresent;

    public GoogleEuiccLpaController(Context context, String key) {
        super(context, key);
        packageManager = context.getPackageManager();

        ApplicationInfo lpa = findLpa(packageManager);
        pkgName = lpa != null ? lpa.packageName : PackageId.G_EUICC_LPA_NAME;
        this.isPresent = lpa != null;
    }

    /** The system LPA package behind the eSIM support switch, or null if there is none. */
    @Nullable
    public static ApplicationInfo findLpa(PackageManager packageManager) {
        for (String pkg : LPA_PACKAGES) {
            try {
                var ai = packageManager.getApplicationInfo(pkg, PackageManager.MATCH_SYSTEM_ONLY);
                if (ai.isSystemApp()) {
                    return ai;
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    @Override
    public boolean isChecked() {
        try {
            return isPresent && packageManager.getApplicationInfo(pkgName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public int getAvailabilityStatus() {
        if (!isPresent) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return ExtSettingControllerHelper.getGlobalSettingAvailability(mContext);
    }
    
    private void setEnabled(boolean isEnabled) {
        String pkg = pkgName;

        // Only Google's LPA ever had its Camera permission granted as system-fixed.
        if (isEnabled && PackageId.G_EUICC_LPA_NAME.equals(pkg)) {
            var permManager = mContext.getSystemService(PermissionManager.class);
            UserHandle user = mContext.getUser();

            String perm = Manifest.permission.CAMERA;
            permManager.revokeRuntimePermission(pkg, perm, user, null);
            // Previously, Camera permission was auto-granted with the FLAG_PERMISSION_SYSTEM_FIXED,
            // which made it unchangeable by the user.
            // Removing FLAG_PERMISSION_USER_FIXED is needed to make sure that the app is always
            // able to show a permission request dialog after being enabled
            int permFlagsToRemove = PackageManager.FLAG_PERMISSION_SYSTEM_FIXED
                    | PackageManager.FLAG_PERMISSION_USER_FIXED;
            permManager.updatePermissionFlags(pkg, perm, permFlagsToRemove, 0, user);
        }

        var powerManager = requireNonNull(mContext.getSystemService(PowerManager.class));

        int ces = isEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                              PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setApplicationEnabledSetting(pkg, ces, 0);

        // Reboot is required in both cases:
        // - OS code doesn't expect LPA to become unavailable at runtime and starts to poll for it
        // with a high frequency
        // - EuiccGoogle performs initialization in response to "(locked) boot completed" broadcasts
        // - also, if current boot count is 1 (i.e. if this is the first boot), EuiccGoogle expects to
        // be interacted with by Google's SetupWizard. Boot count will be at least 2 after reboot.
        powerManager.reboot(null);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        var b = new AlertDialog.Builder(mContext);

        int msg;
        int btnText;
        if (isChecked) {
            msg = R.string.g_euicc_lpa_enable_dialog_msg;
            btnText = R.string.g_euicc_lpa_restart_button;
        } else {
            b.setTitle(R.string.g_euicc_lpa_disable_dialog_title);
            msg = R.string.g_euicc_lpa_disable_dialog_msg;
            btnText = R.string.g_euicc_lpa_proceed_button;
        }

        b.setMessage(msg);
        b.setPositiveButton(btnText, (d, btn) -> setEnabled(isChecked));
        b.show();

        return false;
    }
}
