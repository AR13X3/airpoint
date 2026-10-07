from airpoint.pointer import PointerDriver


class FakeMouse:
    def __init__(self):
        self.x = self.y = 0
        self.pressed = False
        self.presses = 0
        self.centered = 0

    def move(self, dx, dy):
        self.x += dx
        self.y += dy

    def press(self):
        self.pressed = True
        self.presses += 1

    def release(self):
        self.pressed = False

    def center(self):
        self.centered += 1
        self.x = self.y = 0


def drain(driver, ticks=400):
    for _ in range(ticks):
        driver.step()


def test_motion_is_delivered_exactly():
    m = FakeMouse()
    d = PointerDriver(m)
    d.add_motion(100.0, -37.5)
    drain(d)
    assert (m.x, m.y) in ((100, -37), (100, -38))
    assert d.idle


def test_subpixel_motion_accumulates():
    m = FakeMouse()
    d = PointerDriver(m)
    for _ in range(10):
        d.add_motion(0.3, 0.0)
        d.step()
    drain(d)
    assert m.x == 3


def test_smoothing_eases_rather_than_jumps():
    m = FakeMouse()
    d = PointerDriver(m)
    d.set_alpha(0.25)
    d.add_motion(100.0, 0.0)
    d.step()
    assert 0 < m.x < 100


def test_alpha_is_clamped():
    d = PointerDriver(FakeMouse())
    d.set_alpha(5)
    assert d.alpha == 1.0
    d.set_alpha(-1)
    assert d.alpha == 0.05


def test_button_is_reference_counted_across_sessions():
    m = FakeMouse()
    d = PointerDriver(m)
    d.press()
    d.press()
    assert m.presses == 1
    d.release()
    assert m.pressed
    d.release()
    assert not m.pressed
    d.release()  # extra releases are ignored
    assert not m.pressed


def test_center_discards_queued_motion():
    m = FakeMouse()
    d = PointerDriver(m)
    d.add_motion(500, 500)
    d.center()
    drain(d)
    assert (m.x, m.y) == (0, 0) and m.centered == 1
