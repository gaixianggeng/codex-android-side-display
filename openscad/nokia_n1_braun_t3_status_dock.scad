// Braun T3 inspired Nokia N1 desktop status dock.
// Units: millimeters.

part = "assembly"; // [assembly, shell, clips, tablet]

// Nokia N1 dimensions in landscape orientation.
tablet_width = 200.7;
tablet_height = 138.6;
tablet_thickness = 6.9;

// 7.9 inch 4:3 active display, centered on the tablet.
screen_width = 160.5;
screen_height = 120.4;

// Fit and shell depth.
tablet_clearance = 1.4;
front_wall = 4.0;
rear_clearance = 2.4;
body_depth = 40;

// Front layout: speaker grille | Nokia N1 screen | controls.
screen_window_width = 164;
screen_window_height = 110;
screen_corner_radius = 4.5;
screen_bezel_width = 7.5;
screen_center_x = 0;
screen_center_y = 0;

speaker_center_x = 118;
speaker_center_y = 2;
speaker_cols = 13;
speaker_rows = 26;
speaker_pitch = 4.0;
speaker_hole_diameter = 1.65;
speaker_recess_width = 58;
speaker_recess_height = 124;
speaker_recess_depth = 0.55;

controls_center_x = -124;
knob_center_y = 18;
knob_diameter = 32;
knob_raise = 5.2;
knob_chamfer = 1.2;
button_diameter = 6.8;
button_raise = 2.6;
button_spacing = 13.0;
button_center_y = -31;

// Body proportions. The concept image says 263 x 110 mm, but Nokia N1 is
// 138.6 mm tall, so this printable version is scaled to fit the real tablet.
outer_width = 306;
outer_height = 152;
outer_corner_radius = 6;
front_face_recess = 1.1;
front_face_margin = 7;
top_highlight_y = outer_height / 2 - 13;

// Rear loading pocket and retention clips.
pocket_width = tablet_width + tablet_clearance;
pocket_height = tablet_height + tablet_clearance;
pocket_corner_radius = 6;
screw_clearance_diameter = 3.4;
screw_boss_diameter = 11;
screw_boss_height = 4.0;
clip_length = 34;
clip_width = 11;
clip_thickness = 2.8;
clip_hole_offset = 8;
clip_mount_inset_y = 31;
clip_mount_offset_x = 8.5;

// Charging cutout. OpenSCAD's front render mirrors X, so "left" appears on
// the visual right side of the front view used by this design.
charge_side = "left"; // [right, left, bottom]
charge_slot_width = 34;
charge_slot_height = 11;
charge_offset = 0;
cable_groove_depth = 3.4;

// Rear vents and stand.
rear_vent_cols = 12;
rear_vent_rows = 6;
rear_vent_pitch = 5.2;
rear_vent_slot_width = 3.2;
rear_vent_slot_height = 1.2;
use_kickstand = true;
kickstand_span = 176;
kickstand_height = 72;
kickstand_reach = 58;
kickstand_rib_width = 10;

show_tablet_in_assembly = true;

$fn = 56;
eps = 0.05;

side_wall_right = outer_width / 2 - (screen_center_x + pocket_width / 2);
side_wall_left = (screen_center_x - pocket_width / 2) + outer_width / 2;
bottom_wall = outer_height / 2 - pocket_height / 2;
charge_z = front_wall + tablet_thickness / 2;

module rounded_rect_2d(w, h, r) {
    rr = min(r, min(w, h) / 2);
    hull() {
        for (x = [-w / 2 + rr, w / 2 - rr])
            for (y = [-h / 2 + rr, h / 2 - rr])
                translate([x, y]) circle(r = rr);
    }
}

module rounded_box_xy(w, h, d, r) {
    linear_extrude(height = d) rounded_rect_2d(w, h, r);
}

module rounded_ring_xy(outer_w, outer_h, inner_w, inner_h, d, outer_r, inner_r) {
    linear_extrude(height = d) difference() {
        rounded_rect_2d(outer_w, outer_h, outer_r);
        rounded_rect_2d(inner_w, inner_h, inner_r);
    }
}

module front_screen_2d() {
    translate([screen_center_x, screen_center_y])
        rounded_rect_2d(screen_window_width, screen_window_height, screen_corner_radius);
}

module front_screen_cut() {
    translate([0, 0, -knob_raise - eps])
        linear_extrude(height = body_depth + knob_raise + 2 * eps)
            front_screen_2d();
}

module tablet_pocket_cut() {
    translate([screen_center_x, screen_center_y, front_wall])
        linear_extrude(height = body_depth - front_wall + eps)
            rounded_rect_2d(pocket_width, pocket_height, pocket_corner_radius);
}

module shallow_front_recess_cut() {
    translate([0, 0, -eps])
        linear_extrude(height = front_face_recess + eps)
            rounded_rect_2d(
                outer_width - 2 * front_face_margin,
                outer_height - 2 * front_face_margin,
                max(outer_corner_radius - 2, 2)
            );
}

module top_highlight_cut() {
    translate([0, top_highlight_y, -eps])
        rounded_box_xy(outer_width - 34, 1.6, 0.5 + eps, 0.8);
}

module screen_bezel() {
    translate([screen_center_x, screen_center_y, -1.15])
        rounded_ring_xy(
            screen_window_width + 2 * screen_bezel_width,
            screen_window_height + 2 * screen_bezel_width,
            screen_window_width,
            screen_window_height,
            1.25,
            screen_corner_radius + screen_bezel_width,
            screen_corner_radius
        );
}

module screen_glass_preview() {
    color([0.015, 0.015, 0.014, 0.92])
        translate([screen_center_x, screen_center_y, -1.25])
            linear_extrude(height = 0.35)
                rounded_rect_2d(screen_window_width, screen_window_height, screen_corner_radius);
}

module speaker_recess_cut() {
    translate([speaker_center_x, speaker_center_y, -eps])
        rounded_box_xy(speaker_recess_width, speaker_recess_height, speaker_recess_depth + eps, 3);
}

module speaker_holes_cut() {
    for (cx = [0 : speaker_cols - 1])
        for (ry = [0 : speaker_rows - 1])
            translate([
                speaker_center_x + (cx - (speaker_cols - 1) / 2) * speaker_pitch,
                speaker_center_y + (ry - (speaker_rows - 1) / 2) * speaker_pitch,
                -eps
            ])
                cylinder(h = front_wall + 2 * eps, d = speaker_hole_diameter);
}

module speaker_dark_preview() {
    color([0.02, 0.02, 0.018, 1])
        for (cx = [0 : speaker_cols - 1])
            for (ry = [0 : speaker_rows - 1])
                translate([
                    speaker_center_x + (cx - (speaker_cols - 1) / 2) * speaker_pitch,
                    speaker_center_y + (ry - (speaker_rows - 1) / 2) * speaker_pitch,
                    -0.7
                ])
                    cylinder(h = 0.22, d = speaker_hole_diameter * 1.05);
}

module controls() {
    translate([controls_center_x, knob_center_y, -knob_raise])
        cylinder(h = knob_raise + 0.2, d = knob_diameter);
    translate([controls_center_x, knob_center_y, -knob_raise - knob_chamfer])
        cylinder(h = knob_chamfer, d1 = knob_diameter - 2.2, d2 = knob_diameter);

    for (i = [-1, 0, 1])
        translate([controls_center_x + i * button_spacing, button_center_y, -button_raise])
            cylinder(h = button_raise + 0.2, d = button_diameter);
}

module control_tick() {
    translate([controls_center_x + knob_diameter / 2 + 7, knob_center_y + 6, -0.7])
        rotate([0, 0, 34])
            rounded_box_xy(2.0, 13, 0.8, 0.8);
}

module controls_shadow_preview() {
    color([0.12, 0.12, 0.11, 0.55]) {
        translate([controls_center_x, knob_center_y, -knob_raise - 0.2])
            cylinder(h = 0.25, d = knob_diameter * 0.96);
        for (i = [-1, 0, 1])
            translate([controls_center_x + i * button_spacing, button_center_y, -button_raise - 0.18])
                cylinder(h = 0.22, d = button_diameter * 0.9);
    }
}

module front_label_text(label, x, y, size, depth = 0.45) {
    translate([x, y, -depth])
        linear_extrude(height = depth + 0.05)
            mirror([1, 0, 0])
                text(label, size = size, halign = "center", valign = "center", font = "Helvetica:style=Bold");
}

module front_labels_relief() {
    front_label_text("BRAUN", speaker_center_x, -outer_height / 2 + 18, 4.7);
    front_label_text("NOKIA N1", -outer_width / 2 + 34, outer_height / 2 - 20, 3.8);
}

module front_labels_dark_preview() {
    color([0.02, 0.02, 0.018, 1]) {
        front_label_text("BRAUN", speaker_center_x, -outer_height / 2 + 18, 4.7, 0.7);
        front_label_text("NOKIA N1", -outer_width / 2 + 34, outer_height / 2 - 20, 3.8, 0.7);
    }
}

module charge_cutout() {
    if (charge_side == "right") {
        translate([
            screen_center_x + pocket_width / 2 + side_wall_right / 2,
            charge_offset,
            charge_z
        ])
            cube([side_wall_right + 2 * eps, charge_slot_width, charge_slot_height], center = true);
    } else if (charge_side == "left") {
        translate([
            screen_center_x - pocket_width / 2 - side_wall_left / 2,
            charge_offset,
            charge_z
        ])
            cube([side_wall_left + 2 * eps, charge_slot_width, charge_slot_height], center = true);
    } else {
        translate([screen_center_x, -outer_height / 2 + bottom_wall / 2, charge_z])
            cube([charge_slot_width, bottom_wall + 2 * eps, charge_slot_height], center = true);
    }
}

module cable_groove_cut() {
    if (charge_side == "right") {
        translate([
            outer_width / 2 - side_wall_right / 2,
            -outer_height / 2 + 35,
            body_depth - cable_groove_depth / 2 + eps
        ])
            cube([side_wall_right + 2 * eps, 70, cable_groove_depth + 2 * eps], center = true);
    } else if (charge_side == "left") {
        translate([
            -outer_width / 2 + side_wall_left / 2,
            -outer_height / 2 + 35,
            body_depth - cable_groove_depth / 2 + eps
        ])
            cube([side_wall_left + 2 * eps, 70, cable_groove_depth + 2 * eps], center = true);
    } else {
        translate([screen_center_x, -outer_height / 2 + bottom_wall / 2, body_depth - cable_groove_depth / 2 + eps])
            cube([charge_slot_width + 22, bottom_wall + 2 * eps, cable_groove_depth + 2 * eps], center = true);
    }
}

module rear_vent_cuts() {
    for (cx = [0 : rear_vent_cols - 1])
        for (ry = [0 : rear_vent_rows - 1])
            translate([
                screen_center_x + (cx - (rear_vent_cols - 1) / 2) * rear_vent_pitch,
                -outer_height / 2 + 24 + ry * rear_vent_pitch,
                body_depth - eps
            ])
                rounded_box_xy(rear_vent_slot_width, rear_vent_slot_height, 1.2 + eps, 0.5);
}

module clip_positions() {
    for (sx = [-1, 1])
        for (sy = [-1, 1])
            translate([
                screen_center_x + sx * (pocket_width / 2 + clip_mount_offset_x),
                sy * (pocket_height / 2 - clip_mount_inset_y),
                0
            ]) children();
}

module screw_bosses() {
    clip_positions()
        translate([0, 0, body_depth - screw_boss_height])
            cylinder(h = screw_boss_height, d = screw_boss_diameter);
}

module screw_holes() {
    clip_positions()
        translate([0, 0, body_depth - screw_boss_height - eps])
            cylinder(h = screw_boss_height + 2 * eps, d = screw_clearance_diameter);
}

module base_foot() {
    translate([0, -outer_height / 2 - 3.2, 1.0])
        rounded_box_xy(outer_width * 0.86, 7.0, body_depth + 7, 3);
}

module triangular_prism_x(width, p0, p1, p2) {
    x0 = -width / 2;
    x1 = width / 2;
    polyhedron(
        points = [
            [x0, p0[0], p0[1]], [x0, p1[0], p1[1]], [x0, p2[0], p2[1]],
            [x1, p0[0], p0[1]], [x1, p1[0], p1[1]], [x1, p2[0], p2[1]]
        ],
        faces = [
            [0, 2, 1], [3, 4, 5],
            [0, 1, 4, 3], [1, 2, 5, 4], [2, 0, 3, 5]
        ]
    );
}

module kickstand() {
    if (use_kickstand) {
        y0 = -outer_height / 2 + 7;
        y1 = y0 + kickstand_height;
        z0 = body_depth - 1;
        z1 = body_depth + kickstand_reach;
        for (x = [-kickstand_span / 2, kickstand_span / 2])
            translate([x, 0, 0])
                triangular_prism_x(kickstand_rib_width, [y0, z0], [y1, z0], [y0, z1]);

        translate([0, y0 + 5, z1 - 5])
            rounded_box_xy(kickstand_span + kickstand_rib_width, 9, 8, 3);
    }
}

module shell_body() {
    difference() {
        union() {
            difference() {
                union() {
                    rounded_box_xy(outer_width, outer_height, body_depth, outer_corner_radius);
                    screen_bezel();
                    controls();
                    control_tick();
                    front_labels_relief();
                }
                shallow_front_recess_cut();
                top_highlight_cut();
                front_screen_cut();
                tablet_pocket_cut();
                speaker_recess_cut();
                speaker_holes_cut();
            }
            screw_bosses();
            base_foot();
            kickstand();
        }
        charge_cutout();
        cable_groove_cut();
        rear_vent_cuts();
        screw_holes();
    }
}

module retaining_clip() {
    difference() {
        rounded_box_xy(clip_length, clip_width, clip_thickness, 3);
        translate([-clip_length / 2 + clip_hole_offset, 0, -eps])
            cylinder(h = clip_thickness + 2 * eps, d = screw_clearance_diameter);
    }
}

module clip_set() {
    for (i = [0:3])
        translate([(i - 1.5) * (clip_length + 8), 0, 0])
            retaining_clip();
}

module tablet_placeholder() {
    color([0.03, 0.03, 0.03, 0.35])
        translate([screen_center_x, screen_center_y, front_wall])
            rounded_box_xy(tablet_width, tablet_height, tablet_thickness, 6);
    color([0.01, 0.01, 0.01, 0.82])
        translate([screen_center_x, screen_center_y, front_wall - 0.25])
            linear_extrude(height = 0.35)
                rounded_rect_2d(screen_width, screen_height, 2);
}

if (part == "shell") {
    shell_body();
} else if (part == "clips") {
    clip_set();
} else if (part == "tablet") {
    tablet_placeholder();
} else {
    color([0.99, 0.985, 0.965, 1]) shell_body();
    if (show_tablet_in_assembly) tablet_placeholder();
    screen_glass_preview();
    speaker_dark_preview();
    controls_shadow_preview();
    front_labels_dark_preview();
}
