/**
 * 
 */
package featurecreep.loader.eventviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

	private final CopyOnWriteArrayList<EventViewerEvent<?>> events = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<EventListener<?>> listeners = new CopyOnWriteArrayList<>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public EventViewerEvent<?> addEvent(String event_name, Object... params) {

		EventViewerEvent<?> ev;

		if (params.length == 2) {
			ev = new BiAwareEvent(this, event_name, params[0], params[1]);
		} else if (params.length == 1) {
			ev = new MonoAwareEvent(this, event_name, params[0]);
		} else {
			ev = new BasicAwareEvent(this, event_name, params);
		}

		return addEvent(ev);
	}

	public EventViewerEvent<?> addEvent(EventViewerEvent<?> ev) {
		synchronized (events) {
			events.add(ev);
		}
		return ev;
	}

	/**
	 * Finds or creates an event by name, updates its payload, and invokes its listeners.
	 * This convenience method keeps event producers independent from the concrete event
	 * implementation used by the viewer.
	 */
	public Object[] invokeEvent(String event_name, Object... params)
			throws IncompatibleEventListenerMethodReference {
		EventViewerEvent<?> event;
		synchronized (events) {
			// Aware event implementations cache typed parameters, so replace the
			// event rather than mutating only the generic payload array.
			EventViewerEvent<?> existing = getEvent(event_name);
			if (existing != null) {
				events.remove(existing);
			}
			event = addEvent(event_name, params);
		}
		return invokeEvent(event);
	}

	public List<EventViewerEvent<?>> getEvents() {
		return Collections.unmodifiableList(events);
	}

	public EventViewerEvent<?> getEvent(String event_name) {
		for (EventViewerEvent<?> event : events) {
			if (event.getEvent_name().equals(event_name)) {
				return event;
			}
		}

		return null;
	}

	public List<EventListener<?>> getListeners() {
		return Collections.unmodifiableList(listeners);
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerVarArgsListener(String event_name, VarargsFunction<?> function) {
		return this.registerListener(new VarArgsEventListener(this, event_name, function));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerSupplierFunctionListener(String event_name, Supplier<?> function) {
		return this.registerListener(new SupplierEventListener(this, event_name, function));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerMonoFunctionListener(String event_name, Function<?, ?> function) {
		return this.registerListener(new MonoFunctionEventListener(this, event_name, function));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerBiFunctionListener(String event_name, BiFunction<?, ?, ?> function) {
		return this.registerListener(new BiFunctionEventListener(this, event_name, function));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventListener<?> registerObjectListener(String event_name, Object obj) {
		return this.registerListener(new ObjectEventListener(this, event_name, obj));
	}

}
