# Pull Request

## 📝 Description

<!-- Provide a clear and concise description of your changes -->

Fixes #(issue number)

## 🔄 Type of Change

<!-- Mark the relevant option with an "x" -->

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📝 Documentation update
- [ ] 🎨 Code style update (formatting, renaming)
- [ ] ♻️ Refactoring (no functional changes, no api changes)
- [ ] ⚡ Performance improvement
- [ ] ✅ Test update
- [ ] 🔧 Build/CI configuration change
- [ ] 🔒 Security fix

## 📋 Changes Made

<!-- Describe the changes in detail -->

- Added/Modified/Removed X
- Updated Y to support Z
- Fixed issue where ...

## 🧪 Testing

### Test Coverage

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] All tests passing locally
- [ ] Code coverage maintained or improved

### Manual Testing

<!-- Describe how you tested your changes -->

**Test Environment:**
- ElecCloud version: 
- Java version: 
- Database: 
- MQ Type: 

**Test Steps:**
1. 
2. 
3. 

**Test Results:**
<!-- Describe what you observed -->

## 📸 Screenshots (if applicable)

<!-- Add screenshots to demonstrate visual changes -->

| Before | After |
|--------|-------|
| (screenshot) | (screenshot) |

## 📚 Documentation

- [ ] Code is self-documenting (clear variable names, comments where needed)
- [ ] JavaDoc comments added for public APIs
- [ ] README.md updated (if needed)
- [ ] Documentation in `/docs` updated (if needed)
- [ ] CHANGELOG.md updated (for notable changes)
- [ ] Migration guide provided (for breaking changes)

## ⚠️ Breaking Changes

<!-- If this PR introduces breaking changes, describe them here -->

**Before:**
```java
// Old API
retryClient.submit(request);
```

**After:**
```java
// New API
retryClient.submitTask(request);
```

**Migration Path:**
1. Update all calls to use new API
2. Run `mvn clean install` to check for compilation errors

## 🔍 Code Quality

- [ ] Code follows project style guidelines
- [ ] No compiler warnings
- [ ] No code smells or duplication
- [ ] Error handling is appropriate
- [ ] Logging is appropriate (level and content)
- [ ] Security considerations addressed
- [ ] Performance impact considered

## 🔗 Related Issues/PRs

<!-- Link to related issues or PRs -->

- Related to #
- Depends on #
- Blocks #

## ✅ Checklist

<!-- Verify all items before submitting -->

- [ ] My code follows the [Contributing Guidelines](../CONTRIBUTING.md)
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published
- [ ] I have checked my code and corrected any misspellings
- [ ] I have run `mvn clean test` and all tests pass
- [ ] I have updated the version number (if applicable)
- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)

## 💭 Additional Notes

<!-- Add any additional notes for reviewers -->

---

## For Maintainers

<!-- This section is for maintainers only -->

### Review Checklist

- [ ] Code quality is acceptable
- [ ] Tests are adequate
- [ ] Documentation is sufficient
- [ ] No security concerns
- [ ] Breaking changes are justified and documented
- [ ] Changelog updated (if needed)

### Merge Strategy

- [ ] Squash and merge
- [ ] Rebase and merge
- [ ] Create a merge commit

**Target Branch:** `main` / `develop` / `release/X.Y.Z`

**Target Version:** v
