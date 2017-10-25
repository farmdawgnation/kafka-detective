# Kafka Detective Contributing Guide

So, you'd like to make a contribution. Well, we're glad to hear it! This document outlines how
we prefer to receive contributions and what to expect for getting those contributions incorporated.

Broadly, all important contributions and discussion should happen in the GitHub project. This is a
small project, so that's the most sensible thing to do for now. As we grow, we may grow into other
communication mediums.

## Questions

Believe it or not, Questions are an excellent contribution to the project. Over time, they form
a knowledge base of information about the project that will help future users. Please ask questions
using GitHub issues. We'll tag questions with the `question` label in issues so that they're easy
to scan through.

Please search for an existing question issue before creating a new one.

* [Browse questions](https://github.com/farmdawgnation/kafka-detective/issues?utf8=%E2%9C%93&q=is%3Aissue%20label%3Aquestion%20)

## Bug Reports

Bug reports are incredibly important. Even if you're unsure if something is a bug, if you don't
see a prior bug filed for the behavior you're seeing we would prefer you go ahead and file the
report and let us take a look.

Bug reports should include:

1. Steps to reproduce
2. The actual behavior witnessed
3. The expected behavior

Code contributions for tickets tagged as "bug" by the committers are welcome from all, but we
recommend pinging us in the ticket before starting work so we can make sure someone else hasn't
picked it up.

* [Browse open bugs](https://github.com/farmdawgnation/kafka-detective/issues?q=is%3Aopen+is%3Aissue+label%3Abug)

## Enhancement requests

Requests for Enhancements, or RFEs, should also be filed on GitHub. RFEs are the place to discuss
broader design suggestions. In cases where there ends up being a lot of discussion around the design
of an enhancement, an RFE ticket will lead to a separate Enhancement ticket. For cases where the
request seems straightforward in the judgement of the committers and future conversation is likely
to be around the implementation of the ticket, we'll probably skip the RFE process and label.

* [Browse RFEs](https://github.com/farmdawgnation/kafka-detective/issues?utf8=%E2%9C%93&q=is%3Aissue%20label%3Arfe%20)

## Enhancements

Tickets tagged as "Enhancement" are actionable tickets ready for work. Like with bugs, code
contributions are welcome, but please do comment in the ticket to make sure that someone else
isn't already working on the ticket.

* [Browse open enhancements](https://github.com/farmdawgnation/kafka-detective/issues?utf8=%E2%9C%93&q=is%3Aissue%20label%3Aenhancement%20is%3Aopen%20)

## Code Contributions

If you decide to make a code contribution, please follow the following protocol:

1. Fork kafka-detective.
2. Create a new branch for your work. If you reuse "master" in your fork, it becomes annoying to
  check out your code locally.
3. If you already have a fork, we recommend adding this copy of kafka-detective as a remote and
  occasionally synchronizing the master branch.
  1. `git remote add upstream https://github.com/farmdawgnation/kafka-detective.git`
  2. `git checkout master`
  3. `git fetch upstream`
  4. `git merge upstream/master`
  5. `git push origin master`
4. Complete the work on the ticket in your feature branch. Name the branch whatever you like so
  long as it's not `master` or any other long-lived branch name in our fork. Be sure to add tests!
5. Ensure you're following our code style. (See below.) We reserve the right to significantly
  alter modifications that don't conform to our code style before merging.
6. Ensure you're writing good commit messages as you go. We reserve the right to ask you to rebase
  and rewrite your commit messages if we find them difficult to follow.
7. [Open your pull request!](https://github.com/farmdawgnation/kafka-detective/compare)
8. Expect to contribute in a few rounds of code review with one of the committers.
9. Once your contribution passes muster, a committer will merge your change!

### Code Style

Our code style is as follows and, where possible, we'll work to enforce this with automated tooling.

* Two space indents.
* Avoid using single-letter variable names, compacting words by removing vowels, or making
  excessive use of Scala's `_` wildcard.
* `if` blocks always get braces, and the result of the block always gets its own line.
* Name things as descriptively as possible.

When in doubt, ask. We'll update this list as we think of more things that we want to standardize
on.
