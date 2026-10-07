from airpoint.config import ConfigStore
from airpoint.pairing import LOCKOUT, MAX_ATTEMPTS, MAX_LOCKOUT, Pairing


class Clock:
    def __init__(self):
        self.t = 1000.0

    def __call__(self):
        return self.t


def make(tmp_path, clock=None):
    return Pairing(ConfigStore(tmp_path / "config.json"), clock=clock or Clock())


def test_pin_pairs_once_and_issues_token(tmp_path):
    p = make(tmp_path)
    pin = p.pin
    r = p.authenticate("dev1", "Galaxy", token=None, pin=pin)
    assert r.ok and r.token and r.device.name == "Galaxy"
    assert p.pin != pin, "a PIN is single-use"
    # the stored record is a hash, never the token itself
    assert r.token not in (tmp_path / "config.json").read_text()


def test_token_reconnects_without_pin(tmp_path):
    p = make(tmp_path)
    token = p.authenticate("dev1", "Galaxy", None, p.pin).token
    r = p.authenticate("dev1", "Galaxy S24", token=token, pin=None)
    assert r.ok and r.token is None
    assert p.devices[0].name == "Galaxy S24"


def test_tokens_survive_restart(tmp_path):
    p = make(tmp_path)
    token = p.authenticate("dev1", "Galaxy", None, p.pin).token
    p2 = make(tmp_path)
    assert p2.authenticate("dev1", "Galaxy", token, None).ok


def test_missing_or_unknown_token_requires_pairing(tmp_path):
    p = make(tmp_path)
    assert p.authenticate("dev1", "Galaxy", None, None).error == "pairing_required"
    assert p.authenticate("dev1", "Galaxy", "nope", None).error == "pairing_required"


def test_wrong_pin_then_lockout_rotates_pin(tmp_path):
    clock = Clock()
    p = make(tmp_path, clock)
    pin = p.pin
    wrong = "000000" if pin != "000000" else "111111"
    for _ in range(MAX_ATTEMPTS - 1):
        assert p.authenticate("x", "Evil", None, wrong).error == "bad_pin"
    r = p.authenticate("x", "Evil", None, wrong)
    assert r.error == "locked_out" and r.retry_after == LOCKOUT
    assert p.pin != pin
    # even the right (new) PIN is refused during the lockout
    assert p.authenticate("x", "Me", None, p.pin).error == "locked_out"
    clock.t += LOCKOUT + 1
    assert p.authenticate("x", "Me", None, p.pin).ok


def test_pin_accepts_spaces(tmp_path):
    p = make(tmp_path)
    spaced = f"{p.pin[:3]} {p.pin[3:]}"
    assert p.authenticate("d", "Phone", None, spaced).ok


def test_forget_revokes_token(tmp_path):
    p = make(tmp_path)
    r = p.authenticate("dev1", "Galaxy", None, p.pin)
    p.forget(r.device.device_id)
    assert p.authenticate("dev1", "Galaxy", r.token, None).error == "pairing_required"


def test_repairing_same_device_replaces_old_record(tmp_path):
    p = make(tmp_path)
    old = p.authenticate("dev1", "Galaxy", None, p.pin).token
    new = p.authenticate("dev1", "Galaxy", None, p.pin).token
    assert len(p.devices) == 1
    assert not p.authenticate("dev1", "Galaxy", old, None).ok
    assert p.authenticate("dev1", "Galaxy", new, None).ok


def test_lockouts_double_and_reset_after_success(tmp_path):
    clock = Clock()
    p = make(tmp_path, clock)

    def lock_out():
        r = None
        for _ in range(MAX_ATTEMPTS):
            wrong = "000000" if p.pin != "000000" else "111111"
            r = p.authenticate("x", "Evil", None, wrong)
        assert r.error == "locked_out"
        clock.t += r.retry_after + 1
        return r.retry_after

    assert [lock_out() for _ in range(4)] == [LOCKOUT, LOCKOUT * 2, LOCKOUT * 4, LOCKOUT * 8]
    for _ in range(10):
        last = lock_out()
    assert last == MAX_LOCKOUT
    assert p.authenticate("me", "Me", None, p.pin).ok
    assert lock_out() == LOCKOUT
