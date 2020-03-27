
local implicit_precision = 0.25

function BodyTube(cfg)
  return rotate(0, 90, 0) * difference(cylinder(cfg.outer_radius, cfg.length), cylinder(cfg.inner_radius, cfg.length))
end
function TrapezoidFinSet(cfg)
   fins = {}
   for i=1,cfg.fin_count do
      local a_fin = translate(0, cfg.body_radius, -cfg.thickness/2) * linear_extrude(v(0, 0, cfg.thickness), cfg.fin_points)
      table.insert(fins, rotate((i * 360)/cfg.fin_count, 0, 0) * a_fin)
   end
   return translate(cfg.position) * union(fins)
end
function InnerTube(cfg)
   return BodyTube(cfg)
end

function LaunchLug(cfg)
   return translate(v(0, cfg.axial_offset, 0)) * BodyTube(cfg)
end

function ConicalTransition(cfg)
   return translate(cfg.position) * cone(cfg.fore_radius, cfg.aft_radius, v(cfg.length, 0, 0), v(0, 0, 0))
end

function withAftShoudler(shape, cfg)
   local shoulder_pos = cfg.position + v(cfg.length, 0, 0)
   local shoulder = translate(shoulder_pos) * rotate(0, 90, 0) * difference(cylinder(cfg.aft_shoulder.radius, cfg.aft_shoulder.length), cylinder(cfg.aft_shoulder.radius - cfg.aft_shoulder.thickness, cfg.aft_shoulder.length))
   return union{shoulder, shape}
end

function PowerseriesNoseCone(cfg)
      local max_radius = math.max(1, cfg.shape_param) * math.max(cfg.fore_radius, cfg.aft_radius)
   ogive = implicit_solid(
      v(0, -max_radius, -max_radius),
      v(cfg.length, max_radius, max_radius),
      implicit_precision,
[[
uniform float param = 1.0;
uniform float len = 2.5;
uniform float radius = 2.5;
uniform float thickness = 0.3;
float solid(vec3 p) {
	float sdf = length(p.yz) - radius * pow(p.x / len, param);
        return abs(sdf) - thickness;
}
]])
   set_uniform_scalar(ogive, 'len', cfg.length)
   set_uniform_scalar(ogive, 'radius', cfg.aft_radius - cfg.thickness)
   set_uniform_scalar(ogive, 'param', cfg.shape_param)
   set_uniform_scalar(ogive, 'thickness', cfg.thickness)
   return withAftShoudler(ogive, cfg)
end
function TubeCoupler(cfg)
   return BodyTube(cfg)
end

function CenteringRing(cfg)
   return BodyTube(cfg)
end

function ConicalNoseCone(cfg)
   return translate(cfg.position) * cone(cfg.fore_radius, cfg.aft_radius, v(0, 0, 0), v(cfg.length, 0, 0))
end

function pow2(v)
   return v * v
end

function OgiveNoseCone(cfg)
   local max_radius = math.max(1, pow2(cfg.shape_param)) * math.max(cfg.fore_radius, cfg.aft_radius)
   ogive = implicit_solid(
      v(0, -max_radius, -max_radius),
      v(cfg.length, max_radius, max_radius),
      implicit_precision,
[[
uniform float R = 1.0;
uniform float L = 1.0;
uniform float y0 = 0.0;
uniform float thickness = 0.3;
float solid(vec3 p) {
	float sdf = length(p.yz) - (sqrt(pow(R, 2) - pow(L - p.x, 2)) - y0);
        return abs(sdf) - thickness;
}
]])
   local radius = cfg.aft_radius - cfg.thickness;
   local R = math.sqrt((pow2(cfg.length) + pow2(radius)) *
	 (pow2((2 - cfg.shape_param) * cfg.length) + pow2(cfg.shape_param * radius)) / (4 * pow2(cfg.shape_param * radius)))
   local L = cfg.length / cfg.shape_param
   local y0 = math.sqrt(pow2(R) - pow2(L))
   print("" .. R .. "," ..  L .. "," .. y0)
   set_uniform_scalar(ogive, 'R', R)
   set_uniform_scalar(ogive, 'L', L)
   set_uniform_scalar(ogive, 'y0', y0)
   set_uniform_scalar(ogive, 'thickness', cfg.thickness)
   return withAftShoudler(ogive, cfg)
end

function EllipsoidNoseCone(cfg)
   local max_radius = math.max(1.5, pow2(cfg.shape_param)) * math.max(cfg.fore_radius, cfg.aft_radius)
   ogive = implicit_solid(
      v(0, -max_radius, -max_radius),
      v(cfg.length, max_radius, max_radius),
      implicit_precision,
[[
uniform float len = 2.5;
uniform float radius = 2.5;
uniform float thickness = 0.3;
float solid(vec3 p) {
	float x = p.x * radius / len;
	float sdf = length(p.yz) - sqrt(2 * radius * x - x * x);
        return abs(sdf) - thickness;
}
]])
   set_uniform_scalar(ogive, 'len', cfg.length)
   set_uniform_scalar(ogive, 'radius', cfg.aft_radius - cfg.thickness)
   set_uniform_scalar(ogive, 'thickness', cfg.thickness)
   return withAftShoudler(ogive, cfg)
end

function ParabolicNoseCone(cfg)
   local max_radius = math.max(1.5, pow2(cfg.shape_param)) * math.max(cfg.fore_radius, cfg.aft_radius)
   ogive = implicit_solid(
      v(0, -max_radius, -max_radius),
      v(cfg.length, max_radius, max_radius),
      implicit_precision,
[[
uniform float param = 1.0;
uniform float len = 2.5;
uniform float radius = 2.5;
uniform float thickness = 0.3;
float solid(vec3 p) {
        float sdf = length(p.yz) - radius * ((2 * p.x / len - param * pow(p.x / len, 2)) / (2 - param));
        return abs(sdf) - thickness;
}
]])
   set_uniform_scalar(ogive, 'len', cfg.length)
   set_uniform_scalar(ogive, 'radius', cfg.aft_radius - cfg.thickness)
   set_uniform_scalar(ogive, 'param', cfg.shape_param)
   set_uniform_scalar(ogive, 'thickness', cfg.thickness)
   return withAftShoudler(ogive, cfg)
end

function HaackNoseCone(cfg)
   local max_radius = math.max(2.0, pow2(cfg.shape_param)) * math.max(cfg.fore_radius, cfg.aft_radius)
   ogive = implicit_solid(
      v(0, -max_radius, -max_radius),
      v(cfg.length, max_radius, max_radius),
      implicit_precision,
[[
uniform float len = 2.5;
uniform float param = 1.0;
uniform float radius = 2.5;
uniform float thickness = 0.3;
float solid(vec3 p) {
        float theta = acos(1 - 2 * p.x / len);
	float sdf= length(p.yz) - radius * sqrt((theta - sin(2*theta) / 2 + param * pow(sin(theta), 3)));
        return abs(sdf) - thickness;
}
]])
   set_uniform_scalar(ogive, 'len', cfg.length)
   set_uniform_scalar(ogive, 'radius', cfg.aft_radius - cfg.thickness)
   set_uniform_scalar(ogive, 'param', cfg.shape_param)
   set_uniform_scalar(ogive, 'thickness', cfg.thickness)
   return withAftShoudler(ogive, cfg)
end