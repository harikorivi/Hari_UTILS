from hashlib import sha256

import pytest
from printassist.errors import UserError
from printassist.trust_refresh import validate_evidence
from printassist.validation import Trust


def configured(certificates):
    root, leaf, _, _ = certificates
    return Trust(roots=[root],approved_signers={sha256(leaf.dump()).hexdigest()})


def test_fresh_evidence_accepted(certificates):
    _, leaf, _, crl = certificates
    validate_evidence(configured(certificates),leaf,[crl()])


@pytest.mark.parametrize('kind',['stale','revoked','corrupt','missing'])
def test_unusable_refresh_rejected(certificates,kind):
    _, leaf, _, crl = certificates
    evidence = {'stale':[crl(stale=True)], 'revoked':[crl(revoked=True)],
                'corrupt':[b'not signed issuer data'], 'missing':[]}[kind]
    with pytest.raises(UserError,match='No trust files were changed'):
        validate_evidence(configured(certificates),leaf,evidence)


def test_refresh_never_approves_unknown_signer(certificates):
    root, leaf, _, crl=certificates
    with pytest.raises(UserError,match='not approved'):
        validate_evidence(Trust(roots=[root]),leaf,[crl()])
