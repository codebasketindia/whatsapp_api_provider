import { Building2, Bus, Camera, Compass, Hotel, Landmark, MapPin, MoonStar, Plane, Sparkles, Star, Ticket } from 'lucide-react';

/** Icon names partners can pick for a package (stored in the database as text). */
export const PACKAGE_ICONS = { landmark: Landmark, 'moon-star': MoonStar, 'map-pin': MapPin, bus: Bus, plane: Plane, star: Star,
  ticket: Ticket, building: Building2, hotel: Hotel, camera: Camera, compass: Compass, sparkles: Sparkles };

export default function PackageIcon({ name, className }) {
  const Icon = PACKAGE_ICONS[name] || Sparkles;
  return <Icon className={className} aria-hidden />;
}
