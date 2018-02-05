# Changelog
All notable changes to this project will be documented in this file.

## 1.0.6 - 2018-02-03
### Fixed
- Fixed bug where trying to stop the Mongo process would sometimes cause an exception.
## 1.0.5 - 2018-01-18
### Changed
- `startManagedMongoDb` now leaves pre-existing Mongo instances running after the build completes.
## 1.0.4 - 2018-01-17
### Added
- Added Mongo 3.6 support by upgrading to latest version of Flapdoodle.
- Made mongod command-line options configurable.
- Made Mongo access control configurable.
- Made MongoDb server params configurable. 
