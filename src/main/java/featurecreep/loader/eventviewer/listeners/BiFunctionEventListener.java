package featurecreep.loader.eventviewer.listeners;

import java.util.function.BiFunction;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.EventViewerEvent;
import featurecreep.loader.eventviewer.events.BiAwareEvent;

public class BiFunctionEventListener<C, U, T> implements EventListener<T> {

	String event_name;
	EventViewer eventvwr;
	BiFunction<C, U, T> reference;

	public BiFunctionEventListener(EventViewer eventvwr, String event_name, BiFunction<C, U, T> reference) {
		this.event_name = event_name;
		this.eventvwr = eventvwr;
		this.reference = reference;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T invoke() throws IncompatibleEventListenerMethodReference {
		// TODO Auto-generated method stub
		EventViewerEvent<T> event = (EventViewerEvent<T>) eventvwr.getEvent(event_name);
		if (event instanceof BiAwareEvent) {
			BiAwareEvent<C, U, T> mono = (BiAwareEvent<C, U, T>) event;
			return mono.invokeFunction(reference);
		}

		Object[] params = event.getParams();
		C param_0;
		U param_1;
		try {
			param_0 = (C) params[0];
			param_1 = (U) params[1];
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IncompatibleEventListenerMethodReference();
		}

		if (params.length != 2) {
			throw new IncompatibleEventListenerMethodReference();
		}

		return reference.apply(param_0, param_1);
	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}

}
