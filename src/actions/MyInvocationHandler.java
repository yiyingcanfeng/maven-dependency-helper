package actions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author z
 */
public class MyInvocationHandler  implements InvocationHandler {

    private Object target;

    public MyInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return null;
    }
}
