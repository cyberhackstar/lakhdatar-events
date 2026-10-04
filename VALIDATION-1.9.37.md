# v1.9.37 validation plan

- Frontend `verify:baseline`
- Frontend `npm ci --no-audit --no-fund && npm run build`
- Frontend `npm test`
- Backend Maven clean verify
- Flyway migration contract check for V29
- Booking-window unit coverage for an event that starts before `now` and ends in the future
- Release archive excludes generated dependencies/build output
