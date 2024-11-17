/**
 * 
 */
package featurecreep.loader.eventviewer;

import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import featurecreep.loader.eventviewer.events.BasicAwareEvent;
import featurecreep.loader.eventviewer.events.BiAwareEvent;
import featurecreep.loader.eventviewer.events.MonoAwareEvent;
import featurecreep.loader.eventviewer.listeners.BiFunctionEventListener;
import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;
import featurecreep.loader.eventviewer.listeners.MonoFunctionEventListener;
import featurecreep.loader.eventviewer.listeners.ObjectEventListener;
import featurecreep.loader.eventviewer.listeners.ReflectionEventListener;
import featurecreep.loader.eventviewer.listeners.SupplierEventListener;
import featurecreep.loader.eventviewer.listeners.VarArgsEventListener;
import featurecreep.loader.utils.VarargsFunction;

/**
 * @author rhel
 *
 */
public class EventViewer {

	public ArrayList<EventViewerEvent<?>> events = new ArrayList<EventViewerEvent<?>>();
	public ArrayList<EventListener<?>> listeners = new ArrayList<EventListener<?>>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public EventViewerEvent<?> addEvent(String event_name, Object... params) {
		
		EventViewerEvent<?> ev;
		
		if(params.length==2) {
			ev=new BiAwareEvent(this, event_name, params[0], params[1]);
		}else if (params.length==1) {
			ev=new MonoAwareEvent(this, event_name, params[0]);
		}else {
			ev = new BasicAwareEvent(this,event_name, params);
		}
		
		events.add(ev);
		return ev;
	}
	
	public EventViewerEvent<?> addEvent(EventViewerEvent<?> ev) {
		events.add(ev);
		return ev;
	}

	public ArrayList<EventViewerEvent<?>> getEvents() {
		return events;
	}

	public EventViewerEvent<?> getEvent(String event_name) {
		for (EventViewerEvent<?> event : events) {
			if (event.getEvent_name().equals(event_name)) {
				return event;
			}
		}

		return null;
	}

	public ArrayList<EventListener<?>> getListeners() {
		return listeners;
	}

	public ArrayList<EventListener<?>> getListener(String event_name) {
		ArrayList<EventListener<?>> ret = new ArrayList<EventListener<?>>();
		for (EventListener<?> listener : listeners) {
			if (listener.getEventName().equals(event_name)) {
				ret.add(listener);
			}
		}

		return ret;

	}

	public Object[] invokeEvent(EventViewerEvent<?> event) throws IncompatibleEventListenerMethodReference {
		ArrayList<Object> objs = new ArrayList<Object>();
		for (EventListener<?> listener : this.getListener(event.getEvent_name())) {
			objs.add(listener.invoke());
		}

		return objs.toArray();
	}

	public EventListener<?> registerListener(EventListener<?> listener) {
		listeners.add(listener);
		return listener;
	}
	//TODO add NonNull annotations on reflectionlistenertypes
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Deprecated
	public EventListener<?> registerListener(String event_name, String method_name, Object instance,
			Class<?>... param_classes) {
		return this.registerListener(new ReflectionEventListener(this, event_name, method_name, instance, param_classes));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Deprecated
	public EventListener<?> registerStaticReflectionListener(String event_name, String method_name, Class<?> instance,
			Class<?>... param_classes) {
		return this.registerListener(new ReflectionEventListener(this, event_name, method_name, instance, param_classes));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerVarArgsListener(String event_name, VarargsFunction<?> function) {
		return this.registerListener(new VarArgsEventListener(this,event_name, function));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerSupplierFunctionListener(String event_name, Supplier<?> function) {
		return this.registerListener(new SupplierEventListener(this,event_name, function));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerMonoFunctionListener(String event_name, Function<?,?> function) {
		return this.registerListener(new MonoFunctionEventListener(this,event_name, function));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerBiFunctionListener(String event_name, BiFunction<?,?,?> function) {
		return this.registerListener(new BiFunctionEventListener(this,event_name, function));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerObjectListener(String event_name, Object obj) {
		return this.registerListener(new ObjectEventListener(this,event_name, obj));
	}

}

