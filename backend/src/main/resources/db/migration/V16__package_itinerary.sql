-- Package itinerary (rounds / days with places), shown under "Full Details" on the booking page.
ALTER TABLE registration_packages ADD COLUMN itinerary LONGTEXT NULL;
