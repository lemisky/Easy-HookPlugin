package org.foyou.xposed;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Context base = ((Context) param.args[0]);
                ApplicationInfo applicationInfo = null;
                try {
                    applicationInfo = base.getPackageManager().getPackageInfo(lpparam.packageName + ".xposed", 0).applicationInfo;
                } catch (PackageManager.NameNotFoundException e) {
                }
                if (applicationInfo != null) {
                    InjectDex(applicationInfo.publicSourceDir, base.getClassLoader());
                    XposedHelpers.callStaticMethod(lpparam.classLoader.loadClass("org.foyou.HookApp"), "Entry", base);
                }
            }
        });
    }

    /**
     * so注入，添加自定义so库路径
     *
     * @param path
     * @param loader
     */
    public static void InjectSo(String path, ClassLoader loader) {
        ///data/app/org.foyou.mm-2/lib/arm64
        try {
            Field pathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Field nativeLibraryPathElementsField = Class.forName("dalvik.system.DexPathList").getDeclaredField("nativeLibraryPathElements");
            nativeLibraryPathElementsField.setAccessible(true);
            Object nativeElements = nativeLibraryPathElementsField.get(pathListField.get(loader));
            int len = Array.getLength(nativeElements);
            Object mergeElements = Array.newInstance(nativeElements.getClass().getComponentType(), len + 1);
            //生成并添加自定义nativeLibraryPathElement
            try {
                //7.1.2
                Array.set(mergeElements, 0, Class.forName("dalvik.system.DexPathList$Element").getConstructor(File.class, boolean.class, File.class, DexFile.class).newInstance(new File(path), true, null, null));
            } catch (Exception e) {
                //适配9.0
                Class<?> nativeLibraryElementClass = Class.forName("dalvik.system.DexPathList$NativeLibraryElement");
                Constructor<?> nativeLibraryElementConstructor = nativeLibraryElementClass.getDeclaredConstructor(File.class);
                nativeLibraryElementConstructor.setAccessible(true);

                Array.set(mergeElements, 0, nativeLibraryElementConstructor.newInstance(new File(path)));
            }
            for (int i = 1; i <= len; i++) {
                Array.set(mergeElements, i, Array.get(nativeElements, i - 1));
            }
            nativeLibraryPathElementsField.set(pathListField.get(loader), mergeElements);
        } catch (Exception e) {
        }
    }


    public static void InjectDex(String path, ClassLoader loader) {
        //添加自定义加载路径
        //思路：获取类加载路径表，将自己的插在最前面
        try {
            Field pathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Field dexElementsField = Class.forName("dalvik.system.DexPathList").getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            //关键点1，生成自定义dexElements
            Object dElements = dexElementsField.get(pathListField.get(new PathClassLoader(path, null)));
            int dLen = Array.getLength(dElements);
            if (dLen == 0) {
                return;
            }

            Object pElements = dexElementsField.get(pathListField.get(loader));
            int pLen = Array.getLength(pElements);

            if (pLen == 0) {
                dexElementsField.set(pathListField.get(loader), dElements);
                return;
            }
            //关键点2，插入dexElements
            Object mergeElements = Array.newInstance(pElements.getClass().getComponentType(), pLen + dLen);
            int mLen = pLen + dLen;
            for (int i = 0; i < mLen; i++) {
                if (i < dLen) {
                    Array.set(mergeElements, i, Array.get(dElements, i));
                } else {
                    Array.set(mergeElements, i, Array.get(pElements, i - dLen));
                }
            }
            dexElementsField.set(pathListField.get(loader), mergeElements);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

}