package featurecreep.loader.eventviewer.listeners;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.EventViewerEvent;

public class ReflectionEventListener<T> implements EventListener<T>{

	// Method Reference Soon
	//For nonstatic with instance
	public ReflectionEventListener(EventViewer eventvwr, String event_name, String method_name, Object instance,
			Class<?>... param_classes) throws NullPointerException{
		this(eventvwr,event_name,method_name,instance.getClass(),param_classes);
		this.instance=instance;
	}

	//For static
	public ReflectionEventListener(EventViewer eventvwr, String event_name, String method_name, Class<?> clazz,
			Class<?>... param_classes) throws NullPointerException{
		super();
		this.event_name = event_name;
		this.method_name = method_name;
		this.clazz = clazz;
		this.eventvwr = eventvwr;
		this.param_classes=param_classes;
	}
	
	

	public String event_name;
	public String method_name;
	public Object instance;
	public Class<?> clazz;
	public EventViewer eventvwr;
	public Class<?>[] param_classes;

	@SuppressWarnings("unchecked")
	@Override
	public T invoke() {
		EventViewerEvent<T> event = (EventViewerEvent<T>) eventvwr.getEvent(event_name);

		if (event != null) {
			try {
				Method method = clazz.getMethod(method_name, param_classes);
				method.setAccessible(true);
				return (T) method.invoke(instance, event.getParams());
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return null;

	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}
	
}

