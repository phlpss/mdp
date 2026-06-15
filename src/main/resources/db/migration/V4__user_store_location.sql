UPDATE entity_data u
SET payload = jsonb_set(u.payload, '{storeLocationId}', e.payload -> 'locationId', true)
FROM entity_data e
WHERE u.type = 'User'
  AND e.type = 'Employee'
  AND e.id::text = (u.payload ->> 'employeeId')
  AND e.payload ? 'locationId';