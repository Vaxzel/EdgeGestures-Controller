package de.robv.android.xposed;
public abstract class XC_MethodHook {
    public class Unhook {
        public XC_MethodHook getCallback() { return XC_MethodHook.this; }
        public void unhook() {}
    }
    public static class MethodHookParam {
        public Object[] args;
        public Object thisObject;
        public void setResult(Object result) {}
        public Object getResult() { return null; }
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
