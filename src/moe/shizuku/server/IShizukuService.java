package moe.shizuku.server;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: /home/hatch/workspace/apk-analysis/vstool/extracted/classes.dex */
public interface IShizukuService extends IInterface {

    public static class Default implements IShizukuService {
        @Override // moe.shizuku.server.IShizukuService
        public int addUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException {
            return 0;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuService
        public void attachApplication(IShizukuApplication iShizukuApplication, Bundle bundle) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public void attachUserService(IBinder iBinder, Bundle bundle) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public int checkPermission(String str) throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IShizukuService
        public boolean checkSelfPermission() throws RemoteException {
            return false;
        }

        @Override // moe.shizuku.server.IShizukuService
        public void dispatchPackageChanged(Intent intent) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public void dispatchPermissionConfirmationResult(int i, int i2, int i3, Bundle bundle) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public void exit() throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public int getFlagsForUid(int i, int i2) throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IShizukuService
        public String getSELinuxContext() throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuService
        public String getSystemProperty(String str, String str2) throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuService
        public int getUid() throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IShizukuService
        public int getVersion() throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IShizukuService
        public boolean isHidden(int i) throws RemoteException {
            return false;
        }

        @Override // moe.shizuku.server.IShizukuService
        public IRemoteProcess newProcess(String[] strArr, String[] strArr2, String str) throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuService
        public int removeUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IShizukuService
        public void requestPermission(int i) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public void setSystemProperty(String str, String str2) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuService
        public boolean shouldShowRequestPermissionRationale() throws RemoteException {
            return false;
        }

        @Override // moe.shizuku.server.IShizukuService
        public void updateFlagsForUid(int i, int i2, int i3) throws RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements IShizukuService {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
        static final int TRANSACTION_addUserService = 12;
        static final int TRANSACTION_attachApplication = 18;
        static final int TRANSACTION_attachUserService = 102;
        static final int TRANSACTION_checkPermission = 5;
        static final int TRANSACTION_checkSelfPermission = 16;
        static final int TRANSACTION_dispatchPackageChanged = 103;
        static final int TRANSACTION_dispatchPermissionConfirmationResult = 105;
        static final int TRANSACTION_exit = 101;
        static final int TRANSACTION_getFlagsForUid = 106;
        static final int TRANSACTION_getSELinuxContext = 9;
        static final int TRANSACTION_getSystemProperty = 10;
        static final int TRANSACTION_getUid = 4;
        static final int TRANSACTION_getVersion = 3;
        static final int TRANSACTION_isHidden = 104;
        static final int TRANSACTION_newProcess = 8;
        static final int TRANSACTION_removeUserService = 13;
        static final int TRANSACTION_requestPermission = 15;
        static final int TRANSACTION_setSystemProperty = 11;
        static final int TRANSACTION_shouldShowRequestPermissionRationale = 17;
        static final int TRANSACTION_updateFlagsForUid = 107;

        public static class Proxy implements IShizukuService {
            public static IShizukuService sDefaultImpl;
            private IBinder mRemote;

            public Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // moe.shizuku.server.IShizukuService
            public int addUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStrongBinder(iShizukuServiceConnection != null ? iShizukuServiceConnection.asBinder() : null);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (!this.mRemote.transact(12, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().addUserService(iShizukuServiceConnection, bundle);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // moe.shizuku.server.IShizukuService
            public void attachApplication(IShizukuApplication iShizukuApplication, Bundle bundle) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStrongBinder(iShizukuApplication != null ? iShizukuApplication.asBinder() : null);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(18, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().attachApplication(iShizukuApplication, bundle);
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void attachUserService(IBinder iBinder, Bundle bundle) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStrongBinder(iBinder);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(Stub.TRANSACTION_attachUserService, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().attachUserService(iBinder, bundle);
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public int checkPermission(String str) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    if (!this.mRemote.transact(5, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().checkPermission(str);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public boolean checkSelfPermission() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(16, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().checkSelfPermission();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void dispatchPackageChanged(Intent intent) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (intent != null) {
                        parcelObtain.writeInt(1);
                        intent.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(Stub.TRANSACTION_dispatchPackageChanged, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().dispatchPackageChanged(intent);
                } finally {
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void dispatchPermissionConfirmationResult(int i, int i2, int i3, Bundle bundle) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    parcelObtain.writeInt(i2);
                    parcelObtain.writeInt(i3);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(Stub.TRANSACTION_dispatchPermissionConfirmationResult, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().dispatchPermissionConfirmationResult(i, i2, i3, bundle);
                } finally {
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void exit() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (this.mRemote.transact(Stub.TRANSACTION_exit, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().exit();
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public int getFlagsForUid(int i, int i2) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    parcelObtain.writeInt(i2);
                    if (!this.mRemote.transact(Stub.TRANSACTION_getFlagsForUid, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getFlagsForUid(i, i2);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            public String getInterfaceDescriptor() throws RemoteException {
                return Stub.DESCRIPTOR;
            }

            @Override // moe.shizuku.server.IShizukuService
            public String getSELinuxContext() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(9, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getSELinuxContext();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readString();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public String getSystemProperty(String str, String str2) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeString(str2);
                    if (!this.mRemote.transact(10, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getSystemProperty(str, str2);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readString();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public int getUid() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(4, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getUid();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public int getVersion() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(3, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getVersion();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public boolean isHidden(int i) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    if (!this.mRemote.transact(Stub.TRANSACTION_isHidden, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().isHidden(i);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public IRemoteProcess newProcess(String[] strArr, String[] strArr2, String str) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStringArray(strArr);
                    parcelObtain.writeStringArray(strArr2);
                    parcelObtain.writeString(str);
                    if (!this.mRemote.transact(8, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().newProcess(strArr, strArr2, str);
                    }
                    parcelObtain2.readException();
                    return IRemoteProcess.Stub.asInterface(parcelObtain2.readStrongBinder());
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public int removeUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStrongBinder(iShizukuServiceConnection != null ? iShizukuServiceConnection.asBinder() : null);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (!this.mRemote.transact(13, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().removeUserService(iShizukuServiceConnection, bundle);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void requestPermission(int i) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    if (this.mRemote.transact(15, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().requestPermission(i);
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void setSystemProperty(String str, String str2) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeString(str2);
                    if (this.mRemote.transact(11, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().setSystemProperty(str, str2);
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public boolean shouldShowRequestPermissionRationale() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(17, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().shouldShowRequestPermissionRationale();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuService
            public void updateFlagsForUid(int i, int i2, int i3) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    parcelObtain.writeInt(i2);
                    parcelObtain.writeInt(i3);
                    if (this.mRemote.transact(Stub.TRANSACTION_updateFlagsForUid, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().updateFlagsForUid(i, i2, i3);
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShizukuService asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IShizukuService)) ? new Proxy(iBinder) : (IShizukuService) iInterfaceQueryLocalInterface;
        }

        public static IShizukuService getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }

        public static boolean setDefaultImpl(IShizukuService iShizukuService) {
            if (Proxy.sDefaultImpl != null) {
                throw new IllegalStateException("setDefaultImpl() called twice");
            }
            if (iShizukuService == null) {
                return false;
            }
            Proxy.sDefaultImpl = iShizukuService;
            return true;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 3) {
                parcel.enforceInterface(DESCRIPTOR);
                int version = getVersion();
                parcel2.writeNoException();
                parcel2.writeInt(version);
                return true;
            }
            if (i == 4) {
                parcel.enforceInterface(DESCRIPTOR);
                int uid = getUid();
                parcel2.writeNoException();
                parcel2.writeInt(uid);
                return true;
            }
            if (i == 5) {
                parcel.enforceInterface(DESCRIPTOR);
                int iCheckPermission = checkPermission(parcel.readString());
                parcel2.writeNoException();
                parcel2.writeInt(iCheckPermission);
                return true;
            }
            if (i == 1598968902) {
                parcel2.writeString(DESCRIPTOR);
                return true;
            }
            switch (i) {
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    IRemoteProcess iRemoteProcessNewProcess = newProcess(parcel.createStringArray(), parcel.createStringArray(), parcel.readString());
                    parcel2.writeNoException();
                    parcel2.writeStrongBinder(iRemoteProcessNewProcess != null ? iRemoteProcessNewProcess.asBinder() : null);
                    return true;
                case 9:
                    parcel.enforceInterface(DESCRIPTOR);
                    String sELinuxContext = getSELinuxContext();
                    parcel2.writeNoException();
                    parcel2.writeString(sELinuxContext);
                    return true;
                case 10:
                    parcel.enforceInterface(DESCRIPTOR);
                    String systemProperty = getSystemProperty(parcel.readString(), parcel.readString());
                    parcel2.writeNoException();
                    parcel2.writeString(systemProperty);
                    return true;
                case 11:
                    parcel.enforceInterface(DESCRIPTOR);
                    setSystemProperty(parcel.readString(), parcel.readString());
                    parcel2.writeNoException();
                    return true;
                case 12:
                    parcel.enforceInterface(DESCRIPTOR);
                    int iAddUserService = addUserService(IShizukuServiceConnection.Stub.asInterface(parcel.readStrongBinder()), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    parcel2.writeInt(iAddUserService);
                    return true;
                case 13:
                    parcel.enforceInterface(DESCRIPTOR);
                    int iRemoveUserService = removeUserService(IShizukuServiceConnection.Stub.asInterface(parcel.readStrongBinder()), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                    parcel2.writeNoException();
                    parcel2.writeInt(iRemoveUserService);
                    return true;
                default:
                    switch (i) {
                        case 15:
                            parcel.enforceInterface(DESCRIPTOR);
                            requestPermission(parcel.readInt());
                            parcel2.writeNoException();
                            return true;
                        case 16:
                            parcel.enforceInterface(DESCRIPTOR);
                            boolean zCheckSelfPermission = checkSelfPermission();
                            parcel2.writeNoException();
                            parcel2.writeInt(zCheckSelfPermission ? 1 : 0);
                            return true;
                        case 17:
                            parcel.enforceInterface(DESCRIPTOR);
                            boolean zShouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale();
                            parcel2.writeNoException();
                            parcel2.writeInt(zShouldShowRequestPermissionRationale ? 1 : 0);
                            return true;
                        case 18:
                            parcel.enforceInterface(DESCRIPTOR);
                            attachApplication(IShizukuApplication.Stub.asInterface(parcel.readStrongBinder()), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                            parcel2.writeNoException();
                            return true;
                        default:
                            switch (i) {
                                case TRANSACTION_exit /* 101 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    exit();
                                    parcel2.writeNoException();
                                    return true;
                                case TRANSACTION_attachUserService /* 102 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    attachUserService(parcel.readStrongBinder(), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                                    parcel2.writeNoException();
                                    return true;
                                case TRANSACTION_dispatchPackageChanged /* 103 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    dispatchPackageChanged(parcel.readInt() != 0 ? (Intent) Intent.CREATOR.createFromParcel(parcel) : null);
                                    return true;
                                case TRANSACTION_isHidden /* 104 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    boolean zIsHidden = isHidden(parcel.readInt());
                                    parcel2.writeNoException();
                                    parcel2.writeInt(zIsHidden ? 1 : 0);
                                    return true;
                                case TRANSACTION_dispatchPermissionConfirmationResult /* 105 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    dispatchPermissionConfirmationResult(parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                                    return true;
                                case TRANSACTION_getFlagsForUid /* 106 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    int flagsForUid = getFlagsForUid(parcel.readInt(), parcel.readInt());
                                    parcel2.writeNoException();
                                    parcel2.writeInt(flagsForUid);
                                    return true;
                                case TRANSACTION_updateFlagsForUid /* 107 */:
                                    parcel.enforceInterface(DESCRIPTOR);
                                    updateFlagsForUid(parcel.readInt(), parcel.readInt(), parcel.readInt());
                                    parcel2.writeNoException();
                                    return true;
                                default:
                                    return super.onTransact(i, parcel, parcel2, i2);
                            }
                    }
            }
        }
    }

    int addUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException;

    void attachApplication(IShizukuApplication iShizukuApplication, Bundle bundle) throws RemoteException;

    void attachUserService(IBinder iBinder, Bundle bundle) throws RemoteException;

    int checkPermission(String str) throws RemoteException;

    boolean checkSelfPermission() throws RemoteException;

    void dispatchPackageChanged(Intent intent) throws RemoteException;

    void dispatchPermissionConfirmationResult(int i, int i2, int i3, Bundle bundle) throws RemoteException;

    void exit() throws RemoteException;

    int getFlagsForUid(int i, int i2) throws RemoteException;

    String getSELinuxContext() throws RemoteException;

    String getSystemProperty(String str, String str2) throws RemoteException;

    int getUid() throws RemoteException;

    int getVersion() throws RemoteException;

    boolean isHidden(int i) throws RemoteException;

    IRemoteProcess newProcess(String[] strArr, String[] strArr2, String str) throws RemoteException;

    int removeUserService(IShizukuServiceConnection iShizukuServiceConnection, Bundle bundle) throws RemoteException;

    void requestPermission(int i) throws RemoteException;

    void setSystemProperty(String str, String str2) throws RemoteException;

    boolean shouldShowRequestPermissionRationale() throws RemoteException;

    void updateFlagsForUid(int i, int i2, int i3) throws RemoteException;
}
