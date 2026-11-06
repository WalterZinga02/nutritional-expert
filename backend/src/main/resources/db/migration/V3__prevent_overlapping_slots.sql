-- One professional: adjacent slots are allowed, overlapping ones are not.
-- Keep disabled slots in the schedule; they can be re-enabled instead of duplicated.
ALTER TABLE availability_slot ADD CONSTRAINT ex_availability_slot_overlap
    EXCLUDE USING gist (tstzrange(start_time, end_time, '[)') WITH &&);
