package com.matyrobbrt.kaupenbot.util

import com.matyrobbrt.jdahelper.components.ComponentListener
import com.matyrobbrt.jdahelper.components.ComponentManager
import com.matyrobbrt.jdahelper.components.storage.ComponentStorage
import groovy.transform.CompileStatic

@CompileStatic
final class DeferredComponentListeners {
    private ComponentManager manager;
    private final List<ComponentListener> deferredListeners = Collections.synchronizedList(new ArrayList<>());

    ComponentManager createManager(final ComponentStorage storage) {
        this.manager = new ComponentManager(storage, deferredListeners);
        return manager
    }

    ComponentListener.Builder getAt(final String featureId) {
        ComponentListener.builder(featureId) {
            if (manager === null) {
                deferredListeners.add(it)
            } else {
                manager.addListener(it)
            }
        }
    }
}
