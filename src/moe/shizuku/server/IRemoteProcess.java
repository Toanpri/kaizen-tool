package moe.shizuku.server;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ParcelFileDescriptor;

/* JADX INFO: loaded from: /home/hatch/workspace/apk-analysis/vstool/extracted/classes.dex */
public interface IRemoteProcess extends IInterface {

    public static class Default implements IRemoteProcess {
        @Override // moe.shizuku.server.IRemoteProcess
        public boolean alive() throws RemoteException {
            return false;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public void destroy() throws RemoteException {
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public int exitValue() throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public ParcelFileDescriptor getErrorStream() throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public ParcelFileDescriptor getInputStream() throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public ParcelFileDescriptor getOutputStream() throws RemoteException {
            return null;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public int waitFor() throws RemoteException {
            return 0;
        }

        @Override // moe.shizuku.server.IRemoteProcess
        public boolean waitForTimeout(long j, String str) throws RemoteException {
            return false;
        }
    }

    public static abstract class Stub extends Binder implements IRemoteProcess {
        private static final String DESCRIPTOR = "moe.shizuku.server.IRemoteProcess";
        static final int TRANSACTION_alive = 7;
        static final int TRANSACTION_destroy = 6;
        static final int TRANSACTION_exitValue = 5;
        static final int TRANSACTION_getErrorStream = 3;
        static final int TRANSACTION_getInputStream = 2;
        static final int TRANSACTION_getOutputStream = 1;
        static final int TRANSACTION_waitFor = 4;
        static final int TRANSACTION_waitForTimeout = 8;

        public static class Proxy implements IRemoteProcess {
            public static IRemoteProcess sDefaultImpl;
            private IBinder mRemote;

            public Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public boolean alive() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(7, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().alive();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public void destroy() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (this.mRemote.transact(6, parcelObtain, parcelObtain2, 0) || Stub.getDefaultImpl() == null) {
                        parcelObtain2.readException();
                    } else {
                        Stub.getDefaultImpl().destroy();
                    }
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public int exitValue() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(5, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().exitValue();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public ParcelFileDescriptor getErrorStream() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(3, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getErrorStream();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0 ? (ParcelFileDescriptor) ParcelFileDescriptor.CREATOR.createFromParcel(parcelObtain2) : null;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public ParcelFileDescriptor getInputStream() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(2, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getInputStream();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0 ? (ParcelFileDescriptor) ParcelFileDescriptor.CREATOR.createFromParcel(parcelObtain2) : null;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            public String getInterfaceDescriptor() throws RemoteException {
                return Stub.DESCRIPTOR;
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public ParcelFileDescriptor getOutputStream() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(1, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getOutputStream();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0 ? (ParcelFileDescriptor) ParcelFileDescriptor.CREATOR.createFromParcel(parcelObtain2) : null;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public int waitFor() throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(4, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().waitFor();
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // moe.shizuku.server.IRemoteProcess
            public boolean waitForTimeout(long j, String str) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(Stub.DESCRIPTOR);
                    parcelObtain.writeLong(j);
                    parcelObtain.writeString(str);
                    if (!this.mRemote.transact(8, parcelObtain, parcelObtain2, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().waitForTimeout(j, str);
                    }
                    parcelObtain2.readException();
                    return parcelObtain2.readInt() != 0;
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IRemoteProcess asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IRemoteProcess)) ? new Proxy(iBinder) : (IRemoteProcess) iInterfaceQueryLocalInterface;
        }

        public static IRemoteProcess getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }

        public static boolean setDefaultImpl(IRemoteProcess iRemoteProcess) {
            if (Proxy.sDefaultImpl != null) {
                throw new IllegalStateException("setDefaultImpl() called twice");
            }
            if (iRemoteProcess == null) {
                return false;
            }
            Proxy.sDefaultImpl = iRemoteProcess;
            return true;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i == 1598968902) {
                parcel2.writeString(DESCRIPTOR);
                return true;
            }
            switch (i) {
                case 1:
                    parcel.enforceInterface(DESCRIPTOR);
                    ParcelFileDescriptor outputStream = getOutputStream();
                    parcel2.writeNoException();
                    if (outputStream != null) {
                        parcel2.writeInt(1);
                        outputStream.writeToParcel(parcel2, 1);
                    } else {
                        parcel2.writeInt(0);
                    }
                    return true;
                case 2:
                    parcel.enforceInterface(DESCRIPTOR);
                    ParcelFileDescriptor inputStream = getInputStream();
                    parcel2.writeNoException();
                    if (inputStream != null) {
                        parcel2.writeInt(1);
                        inputStream.writeToParcel(parcel2, 1);
                    } else {
                        parcel2.writeInt(0);
                    }
                    return true;
                case 3:
                    parcel.enforceInterface(DESCRIPTOR);
                    ParcelFileDescriptor errorStream = getErrorStream();
                    parcel2.writeNoException();
                    if (errorStream != null) {
                        parcel2.writeInt(1);
                        errorStream.writeToParcel(parcel2, 1);
                    } else {
                        parcel2.writeInt(0);
                    }
                    return true;
                case 4:
                    parcel.enforceInterface(DESCRIPTOR);
                    int iWaitFor = waitFor();
                    parcel2.writeNoException();
                    parcel2.writeInt(iWaitFor);
                    return true;
                case 5:
                    parcel.enforceInterface(DESCRIPTOR);
                    int iExitValue = exitValue();
                    parcel2.writeNoException();
                    parcel2.writeInt(iExitValue);
                    return true;
                case 6:
                    parcel.enforceInterface(DESCRIPTOR);
                    destroy();
                    parcel2.writeNoException();
                    return true;
                case 7:
                    parcel.enforceInterface(DESCRIPTOR);
                    boolean zAlive = alive();
                    parcel2.writeNoException();
                    parcel2.writeInt(zAlive ? 1 : 0);
                    return true;
                case 8:
                    parcel.enforceInterface(DESCRIPTOR);
                    boolean zWaitForTimeout = waitForTimeout(parcel.readLong(), parcel.readString());
                    parcel2.writeNoException();
                    parcel2.writeInt(zWaitForTimeout ? 1 : 0);
                    return true;
                default:
                    return super.onTransact(i, parcel, parcel2, i2);
            }
        }
    }

    boolean alive() throws RemoteException;

    void destroy() throws RemoteException;

    int exitValue() throws RemoteException;

    ParcelFileDescriptor getErrorStream() throws RemoteException;

    ParcelFileDescriptor getInputStream() throws RemoteException;

    ParcelFileDescriptor getOutputStream() throws RemoteException;

    int waitFor() throws RemoteException;

    boolean waitForTimeout(long j, String str) throws RemoteException;
}
