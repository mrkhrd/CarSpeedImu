package android.car.hardware.property;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import java.util.HashMap;
import java.util.Map;

/** Tinnove flavour: CarPropertyEventListener + registerListener/unregisterListener. */
public class CarPropertyManager {
    public static final int VENDOR_ESP = 0x21703442;
    public final Map<CarPropertyEventListener, Map<Integer, Float>> listeners = new HashMap<>();

    public interface CarPropertyEventListener {
        void onChangeEvent(CarPropertyValue value);
        void onErrorEvent(int propId, int zone);
        default void onErrorEvent(int propId, int areaId, int errorCode) {}
    }

    public CarPropertyConfig<?> getCarPropertyConfig(int propId) {
        if (propId == VENDOR_ESP) throw new SecurityException("needs CAR_VENDOR_EXTENSION");
        return new CarPropertyConfig<>();
    }

    public boolean registerListener(CarPropertyEventListener l, int propId, float rate) {
        listeners.computeIfAbsent(l, k -> new HashMap<>()).put(propId, rate);
        return true;
    }

    public void unregisterListener(CarPropertyEventListener l) {
        listeners.remove(l);
    }

    public void fire(int propId, Object value) {
        for (CarPropertyEventListener l : listeners.keySet()) {
            l.onChangeEvent(new CarPropertyValue<>(propId, value));
            l.onErrorEvent(propId, 0, 1);
        }
    }
}
