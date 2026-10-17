package moe.shizuku.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

/* JADX INFO: loaded from: /home/hatch/workspace/apk-analysis/vstool/extracted/classes.dex */
public interface IShizukuApplication extends IInterface {

    public static class Default implements IShizukuApplication {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuApplication
        public void bindApplication(Bundle bundle) throws android.os.RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuApplication
        public void dispatchRequestPermissionResult(int i, Bundle bundle) throws android.os.RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuApplication
        public void showPermissionConfirmation(int i, int i2, String str, int i3) throws android.os.RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements IShizukuApplication {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuApplication";
        static final int TRANSACTION_bindApplication = 2;
        static final int TRANSACTION_dispatchRequestPermissionResult = 3;
        static final int TRANSACTION_showPermissionConfirmation = 10001;

        public static class Proxy implements IShizukuApplication {
            public static IShizukuApplication sDefaultImpl;
            private IBinder mRemote;

            public Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // moe.shizuku.server.IShizukuApplication
            public void bindApplication(Bundle bundle) throws android.os.RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(2, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().bindApplication(bundle);
                } finally {
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuApplication
            public void dispatchRequestPermissionResult(int i, Bundle bundle) throws android.os.RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    if (bundle != null) {
                        parcelObtain.writeInt(1);
                        bundle.writeToParcel(parcelObtain, 0);
                    } else {
                        parcelObtain.writeInt(0);
                    }
                    if (this.mRemote.transact(3, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().dispatchRequestPermissionResult(i, bundle);
                } finally {
                    parcelObtain.recycle();
                }
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // moe.shizuku.server.IShizukuApplication
            public void showPermissionConfirmation(int i, int i2, String str, int i3) throws android.os.RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeInt(i);
                    parcelObtain.writeInt(i2);
                    parcelObtain.writeString(str);
                    parcelObtain.writeInt(i3);
                    if (this.mRemote.transact(Stub.TRANSACTION_showPermissionConfirmation, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().showPermissionConfirmation(i, i2, str, i3);
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

        public static IShizukuApplication asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IShizukuApplication)) ? new Proxy(iBinder) : (IShizukuApplication) iInterfaceQueryLocalInterface;
        }

        public static IShizukuApplication getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }

        public static boolean setDefaultImpl(IShizukuApplication iShizukuApplication) {
            if (Proxy.sDefaultImpl != null) {
                throw new IllegalStateException("setDefaultImpl() called twice");
            }
            if (iShizukuApplication == null) {
                return false;
            }
            Proxy.sDefaultImpl = iShizukuApplication;
            return true;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws android.os.RemoteException {
            if (i == 2) {
                parcel.enforceInterface(DESCRIPTOR);
                bindApplication(parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                return true;
            }
            if (i == 3) {
                parcel.enforceInterface(DESCRIPTOR);
                dispatchRequestPermissionResult(parcel.readInt(), parcel.readInt() != 0 ? (Bundle) Bundle.CREATOR.createFromParcel(parcel) : null);
                return true;
            }
            if (i != TRANSACTION_showPermissionConfirmation) {
                if (i != 1598968902) {
                    return super.onTransact(i, parcel, parcel2, i2);
                }
                parcel2.writeString(DESCRIPTOR);
                return true;
            }
            parcel.enforceInterface(DESCRIPTOR);
            showPermissionConfirmation(parcel.readInt(), parcel.readInt(), parcel.readString(), parcel.readInt());
            parcel2.writeNoException();
            return true;
        }
    }

    void bindApplication(Bundle bundle) throws android.os.RemoteException;

    void dispatchRequestPermissionResult(int i, Bundle bundle) throws android.os.RemoteException;

    void showPermissionConfirmation(int i, int i2, String str, int i3) throws android.os.RemoteException;
}
