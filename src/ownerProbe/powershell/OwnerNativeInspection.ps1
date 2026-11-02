# Nur Deklarationen. Add-Type und sämtliche Methoden erst im separat freigegebenen OS-Lauf.
$script:OwnerNativeDeclaration = @'
using System;
using System.Text;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
namespace MangaLib.OwnerProbe {
  public sealed class NativeIdentity {
    public uint Pid; public long Birth; public string Image;
  }
  public sealed class NativeEnd {
    public bool Ended; public bool ExitObserved; public int ExitCode;
  }
  public static class InspectionV1 {
    const string Dll = @"C:\Windows\System32\kernel32.dll";
    [DllImport(Dll, ExactSpelling=true, SetLastError=true)]
    static extern uint GetProcessId(SafeProcessHandle handle);
    [DllImport(Dll, ExactSpelling=true, SetLastError=true)]
    static extern bool GetProcessTimes(SafeProcessHandle handle, out long birth, out long exit, out long kernel, out long user);
    [DllImport(Dll, ExactSpelling=true, CharSet=CharSet.Unicode, SetLastError=true)]
    static extern bool QueryFullProcessImageNameW(SafeProcessHandle handle, uint flags, StringBuilder path, ref uint size);
    [DllImport(Dll, ExactSpelling=true, SetLastError=true)]
    static extern uint WaitForSingleObject(SafeProcessHandle handle, uint milliseconds);
    [DllImport(Dll, ExactSpelling=true, SetLastError=true)]
    static extern bool GetExitCodeProcess(SafeProcessHandle handle, out uint code);
    [DllImport(Dll, ExactSpelling=true, SetLastError=true)]
    static extern bool CloseHandle(SafeProcessHandle handle);
    static void Check(SafeProcessHandle h) {
      if(h == null || h.IsClosed || h.IsInvalid) throw new InvalidOperationException("OWNER_HANDLE");
    }
    public static NativeIdentity Identity(SafeProcessHandle handle) {
      Check(handle); uint pid=GetProcessId(handle); long birth,exit,kernel,user;
      uint length=512; var path=new StringBuilder(512);
      if(pid==0 || !GetProcessTimes(handle,out birth,out exit,out kernel,out user) || birth<=0 ||
          !QueryFullProcessImageNameW(handle,0,path,ref length) || length<1 || length>=512)
        throw new InvalidOperationException("OWNER_IDENTITY");
      return new NativeIdentity { Pid=pid, Birth=birth, Image=path.ToString() };
    }
    public static NativeEnd End(SafeProcessHandle handle,uint expectedPid,long expectedBirth) {
      Check(handle); long birth,exit,kernel,user;
      if(GetProcessId(handle)!=expectedPid || !GetProcessTimes(handle,out birth,out exit,out kernel,out user) || birth!=expectedBirth)
        throw new InvalidOperationException("OWNER_INSTANCE");
      uint signal=WaitForSingleObject(handle,0);
      if(signal==258) return new NativeEnd();
      if(signal!=0) throw new InvalidOperationException("OWNER_WAIT");
      uint code; if(!GetExitCodeProcess(handle,out code)) throw new InvalidOperationException("OWNER_EXIT");
      return new NativeEnd { Ended=true, ExitObserved=true, ExitCode=unchecked((int)code) };
    }
    public static bool CloseEndedOwner(SafeProcessHandle handle,uint expectedPid,long expectedBirth) {
      var end=End(handle,expectedPid,expectedBirth);
      if(!end.Ended || !end.ExitObserved) return false;
      // Nur der eigene, gehalten und nativ identifizierte beendete Owner.
      // Nach bestätigtem Close den SafeHandle invalidieren: kein zweites Framework-Close.
      if(!CloseHandle(handle)) return false;
      handle.SetHandleAsInvalid(); return true;
    }
  }
}
'@
function Initialize-OwnerNativeInspection {
    if($null -ne ('MangaLib.OwnerProbe.InspectionV1' -as [type])){throw 'OWNER_NATIVE_ALREADY_LOADED'}
    Add-Type -TypeDefinition $script:OwnerNativeDeclaration -ErrorAction Stop
}
