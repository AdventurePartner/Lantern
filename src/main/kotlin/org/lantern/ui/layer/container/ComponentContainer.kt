package org.lantern.ui.layer.container

import org.lantern.ui.IElement
import org.lantern.ui.components.IComponent

class ComponentContainer {
    protected val components = mutableMapOf<String, IComponent>()

    fun addComponent(parent: IElement, component: IComponent) {
        checkNotNull(parent) { "Parent element cannot be null." }
        this.components[component.getUniqueId()] = component
        parent.addChild(component)
    }

    fun findAll(): MutableMap<String, IComponent> {
        return components
    }
}