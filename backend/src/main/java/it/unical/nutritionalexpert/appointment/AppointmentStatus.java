/* Stati del ciclo di vita della visita. Le transizioni consentite sono verificate nei service, non da questo enum. */
package it.unical.nutritionalexpert.appointment;
public enum AppointmentStatus { REQUESTED, CONFIRMED, COMPLETED, CANCELLED }
