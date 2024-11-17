package featurecreep.loader.eventviewer.events;

import java.util.ArrayList;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.EventViewerEvent;
import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;

public interface EventVwrAwareEvent<T> extends EventViewerEvent<T>{

	public EventViewer setEventViewer(EventViewer eventvwr);
	
	public EventViewer getEventViewer();
	
	@SuppressWarnings("unchecked")
	public default ArrayList<EventListener<T>> getListeners(){
		EventViewer vwr = getEventViewer();
		ArrayList<EventListener<T>> ret = new ArrayList<EventListener<T>>();
		for(EventListener<?> listener: vwr.getListeners()) {
			if(listener.getEventName().equals(this.getEvent_name())) {
				ret.add((EventListener<T>)listener);
			}
		}
		return ret;
	}
	
	public default ArrayList<T> invoke() throws IncompatibleEventListenerMethodReference{
		ArrayList<T> ret = new ArrayList<T>();
		for(EventListener<T> listener:getListeners()) {
			ret.add(listener.invoke());
		}
		return ret;
	}
	
}
