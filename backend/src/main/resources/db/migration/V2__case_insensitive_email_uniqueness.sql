-- The application now normalizes every email to lowercase before storing
-- or looking it up (see EmailNormalizer), but this index is the database's
-- own backstop: it guarantees "Maria@x.com" and "maria@x.com" can never
-- both exist as app_user rows, even if some future code path forgets to
-- normalize. V1's existing UNIQUE constraint on email stays in place
-- (redundant now, but harmless, and not worth the risk of dropping a
-- constraint whose auto-generated name isn't pinned anywhere in this repo).
CREATE UNIQUE INDEX app_user_email_lower_idx ON app_user (lower(email));
