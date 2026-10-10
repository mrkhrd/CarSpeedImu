package android.car;

import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;

/** Fake of Tinnove's car-lib, for unit tests only. */
public class Car {
    public static Car last;
    public final CarPropertyManager propertyManager = new CarPropertyManager();
    public boolean disconnected;

    public interface CarServiceLifecycleListener {
        void onLifecycleChanged(Car car, boolean ready);
    }

    public static Car createCar(Context context, Handler handler, long timeout, CarServiceLifecycleListener l) {
        Car car = new Car();
        last = car;
        l.onLifecycleChanged(car, true);
        return car;
    }

    public Object getCarManager(String name) {
        return "property".equals(name) ? propertyManager : null;
    }

    public void disconnect() {
        disconnected = true;
    }
}
