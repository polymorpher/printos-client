package printos.client.usb;

import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.nio.file.Paths;

/**
 * Created by polymorpher on 6/02/15.
 */
public class SyncBulkTransfer {


    private static final short VENDOR_ID = 0x04b8;

    /**
     * The vendor ID of the Samsung Galaxy Nexus.
     */
    private static final short PRODUCT_ID = 0x0e03;

    /**
     * The ADB interface number of the Samsung Galaxy Nexus.
     */
    private static final byte INTERFACE = 0;

    private static final byte IN_ENDPOINT = (byte) 0x82;

    private static final byte OUT_ENDPOINT = 0x01;

    /**
     * The communication timeout in milliseconds.
     */
    private static final int TIMEOUT = 5000;

    /**
     * Writes some data to the device.
     *
     * @param handle The device handle.
     * @param data   The data to send to the device.
     */
    public static void write(DeviceHandle handle, byte[] data) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, OUT_ENDPOINT, buffer,
                transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to send data", result);
        }
        System.out.println(transferred.get() + " bytes sent to device");
    }

    /**
     * Reads some data from the device.
     *
     * @param handle The device handle.
     * @param size   The number of bytes to read from the device.
     * @return The read data.
     */
    public static ByteBuffer read(DeviceHandle handle, int size) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
                ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer,
                transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to read data", result);
        }
        System.out.println(transferred.get() + " bytes read from device");
        return buffer;
    }

    public static void claimDevice(DeviceHandle handle, int interfaceNum) {
        int r = LibUsb.detachKernelDriver(handle, interfaceNum);
        if (r != LibUsb.SUCCESS &&
                r != LibUsb.ERROR_NOT_SUPPORTED &&
                r != LibUsb.ERROR_NOT_FOUND)
            throw new LibUsbException("Unable to detach kernel driver", r);
    }

    /**
     * Main method.
     *
     * @param args Command-line arguments (Ignored)
     * @throws Exception When something goes wrong.
     */
    public static void main(String[] args) throws Exception {
        // Initialize the libusb context
        int result = LibUsb.init(null);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to initialize libusb", result);
        }

        // Open test device (Samsung Galaxy Nexus)
        DeviceHandle handle = LibUsb.openDeviceWithVidPid(null, VENDOR_ID,
                PRODUCT_ID);
        if (handle == null) {
            System.err.println("Test device not found.");
            System.exit(1);
        }

        System.out.println("Detaching kernel...");
        claimDevice(handle, INTERFACE);

        // Claim the ADB interface
        result = LibUsb.claimInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to claim interface", result);
        }

        byte[] bytes = Files.readAllBytes(Paths.get("./test2"));
        // Send ADB CONNECT message
//        write(handle, CONNECT_HEADER);
        write(handle, bytes);

        // Receive the header of the ADB answer (Most likely an AUTH message)
        ByteBuffer header = read(handle, 24);
        header.position(12);
        int dataSize = header.asIntBuffer().get();

        // Receive the body of the ADB answer
        @SuppressWarnings("unused")
        ByteBuffer data = read(handle, dataSize);

        // Release the ADB interface
        result = LibUsb.releaseInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to release interface", result);
        }

        // Close the device
        LibUsb.close(handle);

        // Deinitialize the libusb context
        LibUsb.exit(null);
    }
}