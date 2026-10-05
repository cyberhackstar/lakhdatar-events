# Repository governance

Production branches must require pull requests, required status checks, protected environments, and no direct pushes. Payment, security, infrastructure, database migration, and release-workflow changes should require at least two independent reviewers.

Production deployment uses an exact immutable Git SHA, protected environment approval, verified signed container images, and a tested rollback path. Enterprise certification evidence is kept with the release qualification record.
