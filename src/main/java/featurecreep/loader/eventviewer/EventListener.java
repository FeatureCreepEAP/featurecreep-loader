package featurecreep.loader.eventviewer;

import featurecreep.loader.eventviewer.listeners.IncompatibleEventListenerMethodReference;

public interface EventListener<T> {

	public T invoke() throws IncompatibleEventListenerMethodReference;

	public String getEventName();

}
