-- Preserve cancelled appointments while allowing a slot to be offered again.
ALTER TABLE appointment DROP CONSTRAINT appointment_slot_id_key;
CREATE UNIQUE INDEX uq_appointment_active_slot ON appointment(slot_id) WHERE status <> 'CANCELLED';
