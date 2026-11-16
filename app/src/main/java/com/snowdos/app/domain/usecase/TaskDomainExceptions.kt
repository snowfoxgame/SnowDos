package com.snowdos.app.domain.usecase

class EmptyTaskTitleException : IllegalArgumentException("Titel darf nicht leer sein")

class TaskNotFoundException : NoSuchElementException("Aufgabe nicht gefunden")

class TaskNotActionableException : IllegalStateException("Abgeschlossene Aufgaben können nicht verschoben werden")
