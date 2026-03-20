package featurecreep.loader.eventviewer.listeners;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.EventViewerEvent;
import featurecreep.loader.utils.VarargsFunction;

public class VarArgsEventListener<T> implements EventListener<T> {

	String event_name;
	EventViewer eventvwr;
	VarargsFunction<T> reference;

	public VarArgsEventListener(EventViewer eventvwr, String event_name, VarargsFunction<T> reference) {
		this.event_name = event_name;
		this.eventvwr = eventvwr;
		this.reference = reference;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T invoke() throws IncompatibleEventListenerMethodReference {
		// TODO Auto-generated method stub
		EventViewerEvent<T> event = (EventViewerEvent<T>) eventvwr.getEvent(event_name);
		return reference.apply(event.getParams());
	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}

}
