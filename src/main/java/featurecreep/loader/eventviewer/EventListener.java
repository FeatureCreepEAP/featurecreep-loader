package featurecreep.loader.eventviewer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class EventListener {

	//Method Reference Soon
	public EventListener(EventViewer eventvwr, String event_name, String method_name, Object instance, Class<?> ... param_classes) {
		super();
		this.event_name = event_name;
		this.method_name = method_name;
		this.instance = instance;
		this.eventvwr=eventvwr;
	}
	public String event_name;
	public String method_name;
	public Object instance;
	public EventViewer eventvwr;
public Class<?>[] param_classes;

	public Object invoke() {
EventViewerEvent event = eventvwr.getEvent(event_name);

if(event!=null) {
		try {
			Method method = instance.getClass().getMethod(event_name, param_classes);
		method.setAccessible(true);
		return method.invoke(instance, event.params);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		}
		return null;
		
	}
	
}
