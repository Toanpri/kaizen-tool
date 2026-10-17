package moe.shizuku.server;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: /home/hatch/workspace/apk-analysis/vstool/extracted/classes.dex */
public interface IShizukuServiceConnection extends IInterface {

    public static class Default implements IShizukuServiceConnection {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // moe.shizuku.server.IShizukuServiceConnection
        public void connected(IBinder iBinder) throws RemoteException {
        }

        @Override // moe.shizuku.server.IShizukuServiceConnection
        public void died() throws RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements IShizukuServiceConnection {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuServiceConnection";
        static final int TRANSACTION_connected = 1;
        static final int TRANSACTION_died = 2;

        public static class Proxy implements IShizukuServiceConnection {
            public static IShizukuServiceConnection sDefaultImpl;
            private IBinder mRemote;

            public Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // moe.shizuku.server.IShizukuServiceConnection
            public void connected(IBinder iBinder) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeStrongBinder(iBinder);
                    if (this.mRemote.transact(1, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().connected(iBinder);
                } finally {
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IShizukuServiceConnection
            public void died() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (this.mRemote.transact(2, parcelObtain, null, 1) || Stub.getDefaultImpl() == null) {
                        return;
                    }
                    Stub.getDefaultImpl().died();
                } finally {
                    parcelObtain.recycle();
                }
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShizukuServiceConnection asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IShizukuServiceConnection)) ? new Proxy(iBinder) : (IShizukuServiceConnection) iInterfaceQueryLocalInterface;
        }

        public static IShizukuServiceConnection getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }

        public static boolean setDefaultImpl(IShizukuServiceConnection iShizukuServiceConnection) {
            if (Proxy.sDefaultImpl != null) {
                throw new IllegalStateException("setDefaultImpl() called twice");
            }
            if (iShizukuServiceConnection == null) {
                return false;
            }
            Proxy.sDefaultImpl = iShizukuServiceConnection;
            return true;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 1) {
                parcel.enforceInterface(DESCRIPTOR);
                connected(parcel.readStrongBinder());
                return true;
            }
            if (i == 2) {
                parcel.enforceInterface(DESCRIPTOR);
                died();
                return true;
            }
            if (i != 1598968902) {
                return super.onTransact(i, parcel, parcel2, i2);
            }
            parcel2.writeString(DESCRIPTOR);
            return true;
        }
    }

    void connected(IBinder iBinder) throws RemoteException;

    void died() throws RemoteException;
}
