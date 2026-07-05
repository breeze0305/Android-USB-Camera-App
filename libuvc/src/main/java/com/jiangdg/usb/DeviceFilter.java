package com.jiangdg.usb;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.text.TextUtils;

import com.jiangdg.utils.XLogWrapper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DeviceFilter {
    private static final String TAG = "DeviceFilter";
    private static final int UNSPECIFIED = -1;

    public final int mVendorId;
    public final int mProductId;
    public final int mClass;
    public final int mSubclass;
    public final int mProtocol;
    public final String mManufacturerName;
    public final String mProductName;
    public final String mSerialNumber;
    public final boolean isExclude;

    public DeviceFilter(
            final int vid,
            final int pid,
            final int clasz,
            final int subclass,
            final int protocol,
            final String manufacturer,
            final String product,
            final String serialNum) {
        this(vid, pid, clasz, subclass, protocol, manufacturer, product, serialNum, false);
    }

    public DeviceFilter(
            final int vid,
            final int pid,
            final int clasz,
            final int subclass,
            final int protocol,
            final String manufacturer,
            final String product,
            final String serialNum,
            final boolean isExclude) {
        mVendorId = vid;
        mProductId = pid;
        mClass = clasz;
        mSubclass = subclass;
        mProtocol = protocol;
        mManufacturerName = manufacturer;
        mProductName = product;
        mSerialNumber = serialNum;
        this.isExclude = isExclude;
    }

    public DeviceFilter(final UsbDevice device) {
        this(device, false);
    }

    public DeviceFilter(final UsbDevice device, final boolean isExclude) {
        this(
                device.getVendorId(),
                device.getProductId(),
                device.getDeviceClass(),
                device.getDeviceSubclass(),
                device.getDeviceProtocol(),
                null,
                null,
                null,
                isExclude
        );
    }

    public static List<DeviceFilter> getDeviceFilters(final Context context, final int deviceFilterXmlId) {
        final List<DeviceFilter> filters = new ArrayList<>();
        try {
            final XmlPullParser parser = context.getResources().getXml(deviceFilterXmlId);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && isUsbDeviceTag(parser)) {
                    filters.add(readEntryOne(context, parser));
                }
                eventType = parser.next();
            }
        } catch (final Resources.NotFoundException e) {
            XLogWrapper.w(TAG, "USB device filter resource not found", e);
        } catch (final XmlPullParserException e) {
            XLogWrapper.w(TAG, "USB device filter XML is malformed", e);
        } catch (final IOException e) {
            XLogWrapper.w(TAG, "USB device filter XML could not be read", e);
        }
        return Collections.unmodifiableList(filters);
    }

    public static DeviceFilter readEntryOne(final Context context, final XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (!isUsbDeviceTag(parser)) {
            return null;
        }

        final int vendorId = readInt(context, parser, UNSPECIFIED, "vendor-id", "vendorId", "venderId");
        final int productId = readInt(context, parser, UNSPECIFIED, "product-id", "productId");
        final int deviceClass = readInt(context, parser, UNSPECIFIED, "class");
        final int deviceSubclass = readInt(context, parser, UNSPECIFIED, "subclass");
        final int deviceProtocol = readInt(context, parser, UNSPECIFIED, "protocol");
        final String manufacturer = readString(context, parser, null, "manufacturer-name", "manufacture");
        final String product = readString(context, parser, null, "product-name", "product");
        final String serial = readString(context, parser, null, "serial-number", "serial");
        final boolean exclude = readBoolean(context, parser, false, "exclude");
        return new DeviceFilter(vendorId, productId, deviceClass, deviceSubclass, deviceProtocol,
                manufacturer, product, serial, exclude);
    }

    public boolean matches(final UsbDevice device) {
        if (device == null) {
            return false;
        }
        if (!matchesValue(mVendorId, device.getVendorId())) {
            return false;
        }
        if (!matchesValue(mProductId, device.getProductId())) {
            return false;
        }

        if (matches(device.getDeviceClass(), device.getDeviceSubclass(), device.getDeviceProtocol())) {
            return true;
        }

        final int interfaceCount = device.getInterfaceCount();
        for (int i = 0; i < interfaceCount; i++) {
            final UsbInterface usbInterface = device.getInterface(i);
            if (matches(
                    usbInterface.getInterfaceClass(),
                    usbInterface.getInterfaceSubclass(),
                    usbInterface.getInterfaceProtocol())) {
                return true;
            }
        }
        return false;
    }

    public boolean isExclude(final UsbDevice device) {
        return isExclude && matches(device);
    }

    public boolean matches(final DeviceFilter filter) {
        if (filter == null || isExclude != filter.isExclude) {
            return false;
        }
        return matchesValue(mVendorId, filter.mVendorId)
                && matchesValue(mProductId, filter.mProductId)
                && matchesValue(mClass, filter.mClass)
                && matchesValue(mSubclass, filter.mSubclass)
                && matchesValue(mProtocol, filter.mProtocol)
                && matchesString(mManufacturerName, filter.mManufacturerName)
                && matchesString(mProductName, filter.mProductName)
                && matchesString(mSerialNumber, filter.mSerialNumber);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DeviceFilter) {
            final DeviceFilter other = (DeviceFilter) obj;
            return mVendorId == other.mVendorId
                    && mProductId == other.mProductId
                    && mClass == other.mClass
                    && mSubclass == other.mSubclass
                    && mProtocol == other.mProtocol
                    && isExclude == other.isExclude
                    && exactString(mManufacturerName, other.mManufacturerName)
                    && exactString(mProductName, other.mProductName)
                    && exactString(mSerialNumber, other.mSerialNumber);
        }
        if (obj instanceof UsbDevice) {
            return !isExclude && matches((UsbDevice) obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = mVendorId;
        result = 31 * result + mProductId;
        result = 31 * result + mClass;
        result = 31 * result + mSubclass;
        result = 31 * result + mProtocol;
        result = 31 * result + (mManufacturerName != null ? mManufacturerName.hashCode() : 0);
        result = 31 * result + (mProductName != null ? mProductName.hashCode() : 0);
        result = 31 * result + (mSerialNumber != null ? mSerialNumber.hashCode() : 0);
        result = 31 * result + (isExclude ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DeviceFilter{"
                + "vendorId=" + mVendorId
                + ", productId=" + mProductId
                + ", class=" + mClass
                + ", subclass=" + mSubclass
                + ", protocol=" + mProtocol
                + ", hasManufacturer=" + (mManufacturerName != null)
                + ", hasProduct=" + (mProductName != null)
                + ", hasSerial=" + (mSerialNumber != null)
                + ", exclude=" + isExclude
                + '}';
    }

    private boolean matches(final int clasz, final int subclass, final int protocol) {
        return matchesValue(mClass, clasz)
                && matchesValue(mSubclass, subclass)
                && matchesValue(mProtocol, protocol);
    }

    private static boolean isUsbDeviceTag(final XmlPullParser parser) {
        return parser != null && "usb-device".equalsIgnoreCase(parser.getName());
    }

    private static boolean matchesValue(final int expected, final int actual) {
        return expected == UNSPECIFIED || expected == actual;
    }

    private static boolean matchesString(final String expected, final String actual) {
        return expected == null || expected.equals(actual);
    }

    private static boolean exactString(final String left, final String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static int readInt(
            final Context context,
            final XmlPullParser parser,
            final int defaultValue,
            final String... names) {
        final String rawValue = firstAttribute(parser, names);
        if (TextUtils.isEmpty(rawValue)) {
            return defaultValue;
        }

        try {
            if (isResourceReference(rawValue)) {
                return context.getResources().getInteger(resolveResourceId(context, rawValue));
            }
            return parseIntValue(rawValue);
        } catch (final Resources.NotFoundException | NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean readBoolean(
            final Context context,
            final XmlPullParser parser,
            final boolean defaultValue,
            final String... names) {
        final String rawValue = firstAttribute(parser, names);
        if (TextUtils.isEmpty(rawValue)) {
            return defaultValue;
        }

        try {
            if (isResourceReference(rawValue)) {
                return context.getResources().getBoolean(resolveResourceId(context, rawValue));
            }
            if ("true".equalsIgnoreCase(rawValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(rawValue)) {
                return false;
            }
            return parseIntValue(rawValue) != 0;
        } catch (final Resources.NotFoundException | NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String readString(
            final Context context,
            final XmlPullParser parser,
            final String defaultValue,
            final String... names) {
        final String rawValue = firstAttribute(parser, names);
        if (rawValue == null) {
            return defaultValue;
        }

        try {
            final String value = isResourceReference(rawValue)
                    ? context.getResources().getString(resolveResourceId(context, rawValue))
                    : rawValue;
            return value != null ? value : defaultValue;
        } catch (final Resources.NotFoundException e) {
            return defaultValue;
        }
    }

    private static String firstAttribute(final XmlPullParser parser, final String... names) {
        if (parser == null || names == null) {
            return null;
        }
        for (final String name : names) {
            final String value = parser.getAttributeValue(null, name);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isResourceReference(final String rawValue) {
        return rawValue != null && rawValue.startsWith("@");
    }

    private static int resolveResourceId(final Context context, final String rawValue) {
        final String resourceName = rawValue.substring(1);
        final int resourceId = context.getResources().getIdentifier(resourceName, null, context.getPackageName());
        if (resourceId == 0) {
            throw new Resources.NotFoundException(resourceName);
        }
        return resourceId;
    }

    private static int parseIntValue(final String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            throw new NumberFormatException("blank value");
        }
        final String normalized = rawValue.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            return Integer.parseInt(normalized.substring(2), 16);
        }
        return Integer.parseInt(normalized, 10);
    }
}
