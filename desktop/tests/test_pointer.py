import pytest

from airpoint.pointer import EDGE_ANCHOR_INSET, EDGE_PX_PER_NOTCH, PointerDriver


class FakeMouse:
    def __init__(self, bounds=None):
        self.x = self.y = 0
        self.bounds = bounds  # (min_x, min_y, max_x, max_y) clamps like a real desktop
        self.pressed = False
        self.presses = 0
        self.centered = 0
        self.scrolled_x = self.scrolled_y = 0.0
        self.scroll_points = []

    drop_every = 0  # simulate Windows dropping every Nth move
    _moves = 0

    def move(self, dx, dy):
        self._moves += 1
        if self.drop_every and self._moves % self.drop_every == 0:
            return
        nx, ny = self.x + dx, self.y + dy
        if self.bounds:
            x0, y0, x1, y1 = self.bounds
            nx, ny = min(x1, max(x0, nx)), min(y1, max(y0, ny))
        self.x, self.y = nx, ny

    def scale_at(self, x, y):
        return 1.0

    def edge_overflow(self, x, y):
        if not self.bounds:
            return 0, 0
        x0, y0, x1, y1 = self.bounds
        return x - min(max(x, x0), x1), y - min(max(y, y0), y1)

    def position(self):
        return self.x, self.y

    def scroll(self, dx, dy, at=None):
        self.scrolled_x += dx
        self.scrolled_y += dy
        self.scroll_points.append(at)

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


def edge_driver():
    m = FakeMouse(bounds=(0, 0, 1000, 800))
    m.x, m.y = 500, 790
    return m, PointerDriver(m)


def push(d, dx, dy, frames):
    """Keep pushing like a pen: one phone frame (12 ms) at a time."""
    for _ in range(frames):
        d.add_motion(dx, dy)
        for _ in range(3):
            d.step()
    drain(d)


def test_continued_push_past_the_bottom_scrolls_down_in_proportion():
    m, d = edge_driver()
    push(d, 0, 20, 5)          # reach the edge (the impact itself is absorbed)
    before = m.scrolled_y
    push(d, 0, 12, 50)         # then 600 px of continued push
    assert m.y == 800
    assert m.scrolled_y - before == pytest.approx(-600 / EDGE_PX_PER_NOTCH, abs=1.0)  # +-1 whole-notch slice


def test_a_flick_into_the_edge_does_not_scroll():
    m, d = edge_driver()
    d.add_motion(0, 2000)      # one big flick
    drain(d)
    assert m.y == 800 and m.scrolled_y == 0


def test_wheel_is_aimed_inside_the_screen_not_at_the_edge():
    m, d = edge_driver()
    push(d, 0, 20, 5)
    push(d, 0, 12, 20)
    assert m.scroll_points and all(p == (500, 800 - EDGE_ANCHOR_INSET) for p in m.scroll_points)


def test_top_and_left_edges_scroll_up_and_left():
    m, d = edge_driver()
    m.x, m.y = 10, 10
    push(d, -20, -20, 5)       # reach the corner
    bx, by = m.scrolled_x, m.scrolled_y
    push(d, -12, -12, 20)      # then 240 px of continued push each way
    assert m.scrolled_y - by == pytest.approx(240 / EDGE_PX_PER_NOTCH, abs=1.0)
    assert m.scrolled_x - bx == pytest.approx(-240 / EDGE_PX_PER_NOTCH, abs=1.0)
    assert all(p == (EDGE_ANCHOR_INSET, EDGE_ANCHOR_INSET) for p in m.scroll_points)


def test_free_motion_never_scrolls():
    m, d = edge_driver()
    push(d, -12, -12, 20)
    assert (m.scrolled_x, m.scrolled_y) == (0, 0)


def test_edge_scroll_can_be_turned_off():
    m, d = edge_driver()
    d.set_edge_scroll(False)
    push(d, 0, 12, 60)
    assert m.scrolled_y == 0


def test_no_edge_scroll_while_dragging():
    m, d = edge_driver()
    d.press()
    push(d, 0, 12, 60)
    assert m.scrolled_y == 0


def test_dropped_moves_mid_screen_are_not_mistaken_for_edges():
    m, d = edge_driver()
    m.x, m.y = 500, 400
    m.drop_every = 7
    push(d, 6, 6, 40)
    assert (m.scrolled_x, m.scrolled_y) == (0, 0)
    assert 500 < m.x < 1000 and 400 < m.y < 800


def test_edge_push_still_scrolls_when_moves_are_dropped():
    m, d = edge_driver()
    m.drop_every = 7
    push(d, 0, 20, 5)
    before = m.scrolled_y
    push(d, 0, 12, 50)
    assert m.scrolled_y - before == pytest.approx(-600 / EDGE_PX_PER_NOTCH, abs=1.5)
