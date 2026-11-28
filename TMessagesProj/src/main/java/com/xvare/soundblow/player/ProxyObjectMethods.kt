package com.xvare.soundblow.player

import java.lang.reflect.Method

/**
 * java.lang.reflect.Proxy отправляет в обработчик и методы Object (equals/hashCode/toString). Вернуть для них null нельзя:
 * ListenerSet/CopyOnWriteArraySet вызывают equals и распаковывают boolean (NPE). Здесь — единое правильное поведение.
 */
internal object ProxyObjectMethods {
    fun isObjectMethod(m: Method): Boolean = m.declaringClass == Any::class.java

    /** equals — по ссылке, hashCode — идентичность, toString — имя; остальное для Object не проксируется. */
    fun invoke(proxy: Any, m: Method, args: Array<Any?>?, name: String): Any? = when (m.name) {
        "equals" -> proxy === args?.getOrNull(0)
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "$name@${Integer.toHexString(System.identityHashCode(proxy))}"
        else -> defaultFor(m.returnType)
    }

    /** Значение «по умолчанию» для возвращаемого типа: null в примитив распаковать нельзя. */
    fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
