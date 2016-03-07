package printos.client

import java.nio.{ByteOrder, IntBuffer, ByteBuffer}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import org.usb4java._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

//import ExecutionContext.Implicits.global

/**
  * Created by polymorpher on 3/6/16.
  */

case class PrinterUSBInfo(handler: DeviceHandle,
                          inEndPoint: Byte,
                          outEndPoint: Byte,
                          interfaceId: Int) {
  override def toString(): String = {
    return s"PrinterUSBInfo[\n" +
      s"  handler: ${handler.toString}\n" +
      s"  inEndPoint: ${"%02X" format inEndPoint}\n" +
      s"  outEndPoint: ${"%02X" format outEndPoint}\n" +
      s"  interfaceId: ${interfaceId}\n" +
      s"]\n"
  }
}

class PrintProcessor(info: PrinterUSBInfo) {
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(8))
  type Data = Array[Byte]
  type Job = (Data, printos.client.USBListenerInterface)
  val queue = new java.util.concurrent.ConcurrentLinkedQueue[Job]
  private val shutdown = new AtomicBoolean(false)
  PrinterUtil.bind(info)

  def addJob(data: Data, listener: printos.client.USBListenerInterface): Unit = {
    queue.add((data, listener))
  }

  def shut() = {
    shutdown.set(true)
  }

  def run() = {
    Future {
      while (!shutdown.get()) {
        if (!queue.isEmpty()) {
          try {
            val d = queue.poll()
            PrinterUtil.write(info, d._1) match {
              case Left(s) =>
                d._2.error(s)
              case Right(()) =>
                d._2.complete()
            }
          } catch {
            case e: Exception =>
              println(e)
          }
        }
      }
      println("unbinding: ", info)
      PrinterUtil.unbind(info)
    }
  }
}

object PrinterUtil {
  private val supportedDevices = new mutable.HashSet[String]()
  private val supportedManufacturersAndDevicePatterns = new mutable.HashMap[String, Seq[String]]()
  private val context: Context = new Context
  private val USBTimeout = 5000

  private def getHandle(device: Device): Option[DeviceHandle] = {
    val descriptor: DeviceDescriptor = new DeviceDescriptor
    var result: Int = LibUsb.getDeviceDescriptor(device, descriptor)
    if (result < 0) {
      println("Unable to read device descriptor", result)
      return None
    }
    val h = new DeviceHandle
    result = LibUsb.open(device, h)
    if (result < 0) {
      println(s"Unable to open device: ${LibUsb.strError(result)}")
      return None
    }
    val nm = Option(LibUsb.getStringDescriptor(h, descriptor.iManufacturer())).getOrElse("").toLowerCase()
    val np = Option(LibUsb.getStringDescriptor(h, descriptor.iProduct())).getOrElse("").toLowerCase()
    val fqn = nm + "." + np
    if (supportedDevices.contains(fqn)) {
      return Some(h)
    } else if (supportedManufacturersAndDevicePatterns.contains(nm)) {
      val patterns = supportedManufacturersAndDevicePatterns.get(nm).get
      return patterns.find(p => np.matches(p)).map(p => h)
    }
    return None
  }

  private def getPrinterUSBInfo(device: Device, handle: DeviceHandle): Seq[PrinterUSBInfo] = {
    val deviceDesc: DeviceDescriptor = new DeviceDescriptor
    if (LibUsb.getDeviceDescriptor(device, deviceDesc) < 0) {
      return Seq()
    }
    val infos = ArrayBuffer[PrinterUSBInfo]()
    0 until deviceDesc.bNumConfigurations() map (e => e.toByte) foreach { i =>
      val configDesc: ConfigDescriptor = new ConfigDescriptor
      if (LibUsb.getConfigDescriptor(device, i, configDesc) >= 0) {
        val ifaces = configDesc.iface()
        ifaces.indices.foreach { ifaceId =>
          ifaces(ifaceId).altsetting().foreach { alt =>
            if (DescriptorUtils.getUSBClassName(alt.bInterfaceClass()) == "Printer") {
              var inAddr: Option[Byte] = None
              var outAddr: Option[Byte] = None
              alt.endpoint().foreach { ep =>
                val attr = ep.bmAttributes()
                val addr = ep.bEndpointAddress()
                if (DescriptorUtils.getUsageTypeName(attr) == "Data" && DescriptorUtils.getTransferTypeName(attr) == "Bulk") {
                  if (DescriptorUtils.getDirectionName(addr) == "IN" && inAddr.isEmpty) {
                    inAddr = Some(addr)
                  } else if (DescriptorUtils.getDirectionName(addr) == "OUT" && outAddr.isEmpty) {
                    outAddr = Some(addr)
                  }
                }
              }
              if (inAddr.isDefined && outAddr.isDefined) {
                infos += PrinterUSBInfo(handle, inAddr.get, outAddr.get, ifaceId)
              }
            }
          }
        }
      }
      LibUsb.freeConfigDescriptor(configDesc)
    }
    return infos
  }

  def findPrinters(): Seq[PrinterUSBInfo] = {
    // Read the USB device list
    val list: DeviceList = new DeviceList
    var result = LibUsb.getDeviceList(context, list)
    if (result < 0) {
      throw new LibUsbException("Unable to get device list", result)
    }
    var usbinfos = Seq[PrinterUSBInfo]()

    try {
      import scala.collection.JavaConversions._
      val printConfigs = list.map(getHandle).zip(list).filter(_._1.isDefined).map(e => getPrinterUSBInfo(e._2, e._1.get))
      usbinfos = printConfigs.flatten.toSeq
    } finally {
      LibUsb.freeDeviceList(list, true)
    }
    usbinfos
  }

  def detachKernel(handle: DeviceHandle, interfaceNum: Int) {
    val r: Int = LibUsb.detachKernelDriver(handle, interfaceNum)
    if (r != LibUsb.SUCCESS && r != LibUsb.ERROR_NOT_SUPPORTED && r != LibUsb.ERROR_NOT_FOUND)
      throw new LibUsbException("Unable to detach kernel driver", r)
  }

  def write(pinfo: PrinterUSBInfo, data: Array[Byte]): Either[String, Unit] = {
    val buffer: ByteBuffer = BufferUtils.allocateByteBuffer(data.length)
    buffer.put(data)
    val transferred: IntBuffer = BufferUtils.allocateIntBuffer
    val result: Int = LibUsb.bulkTransfer(pinfo.handler, pinfo.outEndPoint, buffer, transferred, USBTimeout)
    if (result != LibUsb.SUCCESS) {
      val reason = s"USB error ${-result}: Unable to send data: ${LibUsb.strError(result)}"
      Left(reason)
    } else {
      Right(())
    }
  }

  def read(pinfo: PrinterUSBInfo, size: Int): ByteBuffer = {
    val buffer: ByteBuffer = BufferUtils.allocateByteBuffer(size).order(ByteOrder.LITTLE_ENDIAN)
    val transferred: IntBuffer = BufferUtils.allocateIntBuffer
    val result: Int = LibUsb.bulkTransfer(pinfo.handler, pinfo.inEndPoint, buffer, transferred, USBTimeout)
    if (result != LibUsb.SUCCESS) {
      throw new LibUsbException("Unable to read data", result)
    }
    System.out.println(transferred.get + " bytes read from device")
    return buffer
  }

  def init() {
    supportedManufacturersAndDevicePatterns += "epson" -> Seq("tm.*")
    supportedManufacturersAndDevicePatterns += "dascom" -> Seq(".*")
    supportedManufacturersAndDevicePatterns += "icod" -> Seq(".*")
    // Initialize the libusb context
    var result: Int = LibUsb.init(context)
    if (result < 0) {
      throw new LibUsbException("Unable to initialize libusb", result)
    }
  }

  def bind(h: PrinterUSBInfo) = {
    detachKernel(h.handler, h.interfaceId)
    val result = LibUsb.claimInterface(h.handler, h.interfaceId)
    if (result != LibUsb.SUCCESS) {
      throw new LibUsbException("Unable to claim interface", result)
    }
  }

  def unbind(h: PrinterUSBInfo) = {
    val result = LibUsb.releaseInterface(h.handler, h.interfaceId)
    LibUsb.close(h.handler)
    if (result != LibUsb.SUCCESS) {
      throw new LibUsbException("Unable to release interface", result)
    }

  }

  def cleanup(): Unit = {
    LibUsb.exit(context)
  }

  def main(args: Array[String]) {
    init()
    val printers = findPrinters()
    if (printers.size < 1) {
      println("blah")
      return
    }
    println(s"Found ${printers.size} printers")
    val h = printers.head
    bind(h)
    println(h)
    import java.nio.file.{Files, Paths}
    val bytes = Files.readAllBytes(Paths.get("test2"))
    write(h, bytes)
    cleanup()
  }
}
