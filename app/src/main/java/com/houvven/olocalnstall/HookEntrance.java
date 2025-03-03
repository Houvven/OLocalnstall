package com.houvven.olocalnstall;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntrance implements IXposedHookLoadPackage {

    private static final String TAG = "OplusLocalInstallUnlock";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookDevelopmentMode();
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context context = (Context) param.args[0];
                        try {
                            hookMenuItemEnabled(context);
                        } catch (Exception e) {
                            Log.e(TAG, "hook menu item failed.", e);
                        }
                    }
                }
        );
    }

    private static void hookDevelopmentMode() {
        XposedHelpers.findAndHookMethod(
                Settings.Global.class,
                "getInt",
                ContentResolver.class, String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (Objects.equals(param.args[1], "development_settings_enabled")) {
                            Log.i(TAG, "enable development mode.");
                            param.setResult(1);
                        }
                    }
                }
        );
    }

    private static void hookMenuItemEnabled(Context context) throws PackageManager.NameNotFoundException, NoSuchMethodException {
        System.loadLibrary("dexkit");
        ClassLoader cl = context.getClassLoader();

        String apkPath = context.getPackageCodePath();
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        long versionCode = packageInfo.getLongVersionCode();

        SharedPreferences preferences = context.getSharedPreferences("dex_kit", Context.MODE_PRIVATE);
        long cachedVersionCode = preferences.getLong("version_code", 0);

        Method targetHookMethod1;
        Method targetHookMethod2;

        if (versionCode != cachedVersionCode) {
            Log.i(TAG, "cache miss, try to find method.");
            MethodMatcher methodMatcher1 = MethodMatcher.create()
                    .modifiers(Modifier.STATIC)
                    .paramCount(0)
                    .returnType(boolean.class)
                    .addUsingString("update_state")
                    .addUsingString("OTAStrategy")
                    .addUsingString("is_local_update")
                    .addUsingString("allowLocalUpdateReChoice isLocalUpdate");

            MethodMatcher methodMatcher2 = MethodMatcher.create()
                    .modifiers(Modifier.STATIC)
                    .paramCount(1)
                    .paramTypes(Context.class)
                    .returnType(boolean.class)
                    .addUsingString("update_state")
                    .addUsingString("OTAStrategy")
                    .addUsingString("isNoneLocalUpdateDisabled")
                    .addUsingString("isLocalUpdateDisabled")
                    .addUsingString("isUpdateEngineRunning")
                    .addUsingString("isMerging");

            try (DexKitBridge dexKit = DexKitBridge.create(apkPath)) {
                MethodData m1 = dexKit.findMethod(FindMethod.create().matcher(methodMatcher1)).single();
                MethodData m2 = dexKit.findMethod(FindMethod.create().matcher(methodMatcher2)).single();
                Log.i(TAG, String.format("found method1 = %s, method2 = %s", m1.getMethodName(), m2.getMethodName()));

                preferences.edit()
                        .putLong("version_code", versionCode)
                        .putString("method_is_local_update", m1.toDexMethod().serialize())
                        .putString("method_is_local_update_disabled", m2.toDexMethod().serialize())
                        .apply();

                targetHookMethod1 = m1.getMethodInstance(cl);
                targetHookMethod2 = m2.getMethodInstance(cl);
            } catch (Exception e) {
                Log.e(TAG, "find method failed.", e);
                throw e;
            }
        } else {
            Log.i(TAG, "cache hit, use cached method.");
            String descriptor1 = preferences.getString("method_is_local_update", null);
            String descriptor2 = preferences.getString("method_is_local_update_disabled", null);
            targetHookMethod1 = new DexMethod(Objects.requireNonNull(descriptor1)).getMethodInstance(cl);
            targetHookMethod2 = new DexMethod(Objects.requireNonNull(descriptor2)).getMethodInstance(cl);
        }

        XposedBridge.hookMethod(targetHookMethod1, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });

        XposedBridge.hookMethod(targetHookMethod2, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                param.setResult(false);
            }
        });
    }
}
